// Owned Redis process and aggregate-only sampling for the existing HTTP benchmark driver.
import { execFile } from 'node:child_process'
import { promisify } from 'node:util'
import { randomUUID, createHash } from 'node:crypto'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
const exec = promisify(execFile)
const sha = path => createHash('sha256').update(readFileSync(path)).digest('hex')
const stateScript = `
local cursor='0'; local domains=0; local reservations=0; local terminals=0
local maxReservations=0; local maxTerminals=0; local maxBytes=0
local minTtl=2147483647; local maxTtl=0; local scans=0
repeat
  local batch=redis.call('SCAN',cursor,'MATCH','io.inertia.delivery.{capacity:*}','COUNT',100)
  cursor=batch[1]; scans=scans+1
  if scans>64 then return redis.error_reply('Capacity sampler scan bound') end
  for _,key in ipairs(batch[2]) do
    domains=domains+1
    if domains>4096 then return redis.error_reply('Capacity sampler domain bound') end
    local raw=redis.call('GET',key)
    if raw then
      if string.len(raw)>1048576 then return redis.error_reply('Capacity sampler byte bound') end
      local value=cjson.decode(raw); local r=0; local t=0
      for _ in pairs(value.reserved) do r=r+1 end
      for _ in pairs(value.terminals) do t=t+1 end
      reservations=reservations+r; terminals=terminals+t
      maxReservations=math.max(maxReservations,r); maxTerminals=math.max(maxTerminals,t)
      maxBytes=math.max(maxBytes,string.len(raw))
      local ttl=redis.call('PTTL',key); minTtl=math.min(minTtl,ttl); maxTtl=math.max(maxTtl,ttl)
    end
  end
until cursor=='0'
return {domains,reservations,terminals,maxReservations,maxTerminals,maxBytes,domains==0 and -2 or minTtl,maxTtl}
`
export async function ownedRedis({ start, wait, reservePort }) {
  const executable = process.env.INERTIA_BENCH_REDIS_SERVER
  const cli = process.env.INERTIA_BENCH_REDIS_CLI
  if (!executable || !cli) throw new Error('Redis capacity requires explicit redis-server and redis-cli executables')
  const fixture = process.env.INERTIA_BENCH_REDIS_TLS_FIXTURE
  const port = await reservePort()
  const password = randomUUID()
  const args = ['--bind', '127.0.0.1', '--save', '', '--appendonly', 'no', '--requirepass', password,
    '--maxmemory', '64mb', '--maxmemory-policy', 'noeviction']
  const client = ['--json', '-h', fixture ? 'localhost' : '127.0.0.1', '-p', String(port)]
  if (fixture) {
    args.push('--port','0','--tls-port',String(port),'--tls-auth-clients','no',
      '--tls-cert-file',resolve(fixture,'server.crt'),'--tls-key-file',resolve(fixture,'server.key'),
      '--tls-ca-cert-file',resolve(fixture,'ca.crt'))
    client.push('--tls','--cacert',resolve(fixture,'ca.crt'))
  } else args.push('--port',String(port))
  const processHandle = start(executable,args,undefined,'capacity-redis')
  await wait('owned Redis startup', () => {
    if (processHandle.exitCode !== null || processHandle.signalCode !== null) throw new Error('Owned Redis exited')
    return processHandle.transcript.includes('Ready to accept connections')
  })
  let cliConnections = 0
  async function command(...args) {
    cliConnections++
    const { stdout } = await exec(cli,[...client,...args],{env:{...process.env,REDISCLI_AUTH:password},timeout:5000,maxBuffer:1024*1024})
    // redis-cli deliberately renders INFO as raw text even with --json.
    const value = args[0] === 'INFO' ? stdout : JSON.parse(stdout)
    if (value && typeof value === 'object' && value.error) throw new Error('Redis aggregate sample failed')
    return value
  }
  if (await command('PING') !== 'PONG') throw new Error('Owned Redis health failed')
  const properties = {
    'spring.autoconfigure.exclude':'org.springframework.boot.autoconfigure.session.SessionAutoConfiguration',
    'inertia.session.store':'redis', 'inertia.session-namespace':'capacity',
    'inertia.session.redis.host':fixture ? 'localhost' : '127.0.0.1',
    'inertia.session.redis.port':port,'inertia.session.redis.password':password,
    'inertia.session.redis.command-timeout':'1s','inertia.session.redis.max-commands':16,
    'inertia.session.redis.idle-ttl':'2m','inertia.session.redis.lease':'10s',
    'inertia.session.redis.terminal-retention':'30s','inertia.session.redis.max-reservations':16,
    'inertia.session.redis.max-terminals':4096,'inertia.session.redis.max-attempts':16,
  }
  if (fixture) Object.assign(properties,{
    'inertia.session.redis.tls.enabled':true,
    'inertia.session.redis.tls.trust-store':resolve(fixture,'trust.p12'),
    'inertia.session.redis.tls.trust-store-password':'local-fixture-store',
  })
  return {
    process:processHandle,
    env:{SPRING_APPLICATION_JSON:JSON.stringify(properties)},
    identity:{serverSha256:sha(executable),cliSha256:sha(cli),tls:Boolean(fixture),
      certificateSha256:fixture ? sha(resolve(fixture,'server.crt')):null,
      version:(await exec(executable,['--version'])).stdout.trim()},
    async sample() {
      const info = await command('INFO','all')
      if (typeof info !== 'string') throw new Error('Redis INFO sample shape')
      const values = Object.fromEntries(info.split(/\r?\n/).filter(line=>/^[a-z_]+:\d+$/.test(line)).map(line=>{
        const [key,value]=line.split(':'); return [key,Number(value)]
      }))
      const names = ['used_memory','used_memory_peak','connected_clients','total_connections_received',
        'total_commands_processed','expired_keys','evicted_keys']
      if (names.some(name=>!Number.isSafeInteger(values[name]))) throw new Error('Incomplete Redis numeric sample')
      const state = await command('EVAL',stateScript,'0')
      if (!Array.isArray(state) || state.length!==8 || state.some(value=>!Number.isSafeInteger(value))) throw new Error('Redis state sample shape')
      return { ...Object.fromEntries(names.map(name=>[name,values[name]])), cliConnections,
        domains:state[0],reservations:state[1],terminals:state[2],maxDomainReservations:state[3],
        maxDomainTerminals:state[4],maxEnvelopeBytes:state[5],minTtlMs:state[6],maxTtlMs:state[7] }
    },
  }
}
