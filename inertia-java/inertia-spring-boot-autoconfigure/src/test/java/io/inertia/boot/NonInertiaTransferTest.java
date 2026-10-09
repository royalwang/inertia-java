package io.inertia.boot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import io.inertia.core.*;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.http.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

class NonInertiaTransferTest {
  static final byte[] BYTES = {0, 1, 2, 60, 47, 115, 99, 114, 105, 112, 116, 62, -1, -2};
  final WebApplicationContextRunner runner =
      new WebApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(InertiaAutoConfiguration.class, WebMvcAutoConfiguration.class))
          .withBean(InertiaConfig.class, () -> InertiaConfig.basic("current", Set.of("Home")))
          .withBean(Transfers.class, Transfers::new);

  @RestController
  static class Transfers {
    final AtomicInteger uploads = new AtomicInteger();
    final AtomicInteger downloads = new AtomicInteger();

    @PostMapping("/rest/upload")
    ResponseEntity<byte[]> upload(@RequestParam MultipartFile file) throws Exception {
      uploads.incrementAndGet();
      return ResponseEntity.status(201)
          .contentType(MediaType.APPLICATION_OCTET_STREAM)
          .header("X-Transfer", "upload")
          .body(file.getBytes());
    }

    @GetMapping("/rest/download")
    ResponseEntity<StreamingResponseBody> download() {
      downloads.incrementAndGet();
      return ResponseEntity.ok()
          .contentType(MediaType.APPLICATION_OCTET_STREAM)
          .header("Content-Disposition", "attachment; filename=example.bin")
          .header("Cache-Control", "public, max-age=60")
          .body(stream -> stream.write(BYTES));
    }
  }

  @Test
  void multipartUploadStaysBinaryAndDoesNotEnterVersionOrSessionPolicy() {
    runner.run(
        context -> {
          assertThat(context).hasNotFailed();
          var result =
              MockMvcBuilders.webAppContextSetup(context.getSourceApplicationContext())
                  .build()
                  .perform(
                      multipart("/rest/upload")
                          .file(
                              new MockMultipartFile(
                                  "file", "example.bin", "application/octet-stream", BYTES))
                          .header("X-Inertia", "true")
                          .header("X-Inertia-Version", "stale")
                          .header("X-Inertia-Partial-Data", "anything")
                          .header("X-Inertia-Prefetch", "true"))
                  .andExpect(status().isCreated())
                  .andExpect(content().bytes(BYTES))
                  .andExpect(header().string("X-Transfer", "upload"))
                  .andExpect(header().doesNotExist("X-Inertia"))
                  .andExpect(header().doesNotExist("X-Inertia-Location"))
                  .andExpect(header().doesNotExist("Vary"))
                  .andReturn();
          assertThat(context.getBean(Transfers.class).uploads.get()).isEqualTo(1);
          assertThat(result.getRequest().getSession(false)).isNull();
        });
  }

  @Test
  void asynchronousDownloadRetainsBytesBusinessHeadersAndSpringAsyncHandling() {
    runner.run(
        context -> {
          assertThat(context).hasNotFailed();
          var mvc =
              MockMvcBuilders.webAppContextSetup(context.getSourceApplicationContext()).build();
          var initial =
              mvc.perform(
                      get("/rest/download")
                          .header("X-Inertia", "true")
                          .header("X-Inertia-Version", "stale"))
                  .andExpect(request().asyncStarted())
                  .andReturn();
          var result =
              mvc.perform(asyncDispatch(initial))
                  .andExpect(status().isOk())
                  .andExpect(content().bytes(BYTES))
                  .andExpect(content().contentType("application/octet-stream"))
                  .andExpect(
                      header().string("Content-Disposition", "attachment; filename=example.bin"))
                  .andExpect(header().string("Cache-Control", "public, max-age=60"))
                  .andExpect(header().doesNotExist("X-Inertia"))
                  .andExpect(header().doesNotExist("Vary"))
                  .andReturn();
          assertThat(context.getBean(Transfers.class).downloads.get()).isEqualTo(1);
          assertThat(result.getRequest().getSession(false)).isNull();
        });
  }
}
