---
title: "Create your first Spring application"
description: "Assemble a new consuming project, InertiaConfig, assets and a controller outside the example checkout."
version: 0.1.0-SNAPSHOT
sources:
  - inertia-java/docs/examples/first-application/pom.xml
  - inertia-java/docs/examples/first-application/HelloController.java
  - inertia-java/docs/examples/first-application/Hello.tsx
  - inertia-java/docs/scripts/create-first-application.mjs
verification:
  - inertia-java/docs/scripts/verify-first-application.mjs
  - inertia-java/scripts/verify.mjs
  - inertia-java/scripts/verify-maven-consumer.py
---

# Create your first Spring application

Create an independent Maven application outside the repository, add a Java route and a React page, then verify real SSR and a form round trip. This tutorial starts from the verified example's asset, root-template and security wiring so the result includes a working browser application.

The generated project retains the original demo routes as reference material. Its own `/hello` route is the tutorial's application code. There is no database or persisted business mutation.

## Install the library and create the project

From `inertia-java/`, first run [the local installation](installation.md):

```sh
./mvnw install
node docs/scripts/create-first-application.mjs /tmp/my-inertia-app
```

The destination must not already exist and must be outside `inertia-java/`. Choose another absolute directory if `/tmp/my-inertia-app` is occupied. The script refuses an existing target rather than merging into your work.

It copies the sample's Java main sources and locked frontend build inputs, adds the canonical tutorial files, and creates an independent `com.example:first-inertia-app:1.0.0-SNAPSHOT` POM. That POM imports Boot's BOM and resolves the starter as a dependency. It has no reactor parent, relative parent path or source reference back into this checkout.

The creation script is a repository development tool; it is not a Maven archetype or a command installed by the starter. Retain LICENSE and NOTICE with copied original source.

The independent POM is imported directly from the file copied by the creation script:

<<< @/examples/first-application/pom.xml

## Understand the route and page

The canonical [HelloController.java](https://github.com/royalwang/inertia-java/blob/main/inertia-java/docs/examples/first-application/HelloController.java) defines:

| Request | Result |
| --- | --- |
| `GET /hello` | `Hello` component, `message` prop and private/no-store caching |
| `POST /hello` with a missing/blank/oversize name | Queue `name` validation feedback and redirect to `/hello` |
| `POST /hello` with a valid string | Queue one `toast` flash message and redirect to `/hello` |

<<< @/examples/first-application/HelloController.java

The Java handler returns `InertiaResponse` or `HttpOutcome` from an ordinary `@Controller`. It validates a string value before using it and never reflects rejected input into an exception page.

The canonical [Hello.tsx](https://github.com/royalwang/inertia-java/blob/main/inertia-java/docs/examples/first-application/Hello.tsx) uses `useForm`, `usePage`, `Head` and `Link`. The form sends the official client's normal request; the retained Spring Security wiring issues and checks CSRF tokens. Feedback arrives on the redirected Page, rather than as a custom JSON 422 API.

<<< @/examples/first-application/Hello.tsx

The creation script registers `Hello` in **both** the Java component set and the frontend registry used by browser and Node entries. To add another page, update those two registries and create its controller/component. Changing only a filename is insufficient.

## Build the independent project

From `inertia-java/`, use the pinned wrapper to build the external POM:

```sh
./mvnw -f /tmp/my-inertia-app/pom.xml package
```

From `/tmp/my-inertia-app/frontend/`:

```sh
npm ci
npm run typecheck
npm run build
```

The frontend still uses the example's coherent manifest, build receipt and immutable asset store. `npm ci` uses the copied lockfile. No Maven reactor source is compiled into the application; the library jars resolve from the local repository populated during installation.

## Run and verify

Stop any quick-start processes using the default ports. Terminal 1, from `/tmp/my-inertia-app/frontend/`:

```sh
npm run ssr
```

Terminal 2, from `/tmp/my-inertia-app/`:

```sh
java -jar target/first-inertia-app-1.0.0-SNAPSHOT.jar --server.address=127.0.0.1 --server.port=18080
```

Open [the hello page](http://127.0.0.1:18080/hello). Confirm each boundary:

1. Disable JavaScript and reload: **Hello from Java** is present in the document.
2. Enable JavaScript: submitting a blank name displays the validation message.
3. Submit `Ada`: **Hello, Ada** appears as flash; reload and confirm it disappears.
4. Follow the About link and return using browser history; navigation and the form remain usable.
5. Inspect a request with `X-Inertia: true`: it returns component `Hello` and Page props as JSON.
6. Stop the owned Node process: the default response remains usable through CSR with JavaScript enabled.

`npm run docs:first-application` in the documentation directory automates generation, Maven/npm builds and these HTTP/browser boundaries in a unique temporary project. It requires installed local library artifacts and an installed Playwright browser; see the documentation README for the exact preparation commands. It writes a summary rather than treating compilation alone as SSR acceptance.

## Adapt it to your application

Move or rename the sample Java package and update the Boot plugin's `mainClass` together. Replace demo routes and component registrations deliberately. Keep the root view, codec, SSR root ID, build receipt and static asset mount consistent. Add your actual authentication, authorization, persistence and production session/cookie policy before introducing protected data.

The [ownership model](../concepts/ownership.md) explains which objects can be shared and which must remain request-owned. The [API guide](../api-guide.md) covers configuration replacement and extension points.
