package com.adarsh.urlshortener;

import tools.jackson.databind.ObjectMapper;
import com.adarsh.urlshortener.dto.ShortenRequest;
import com.adarsh.urlshortener.dto.ShortenResponse;
import com.adarsh.urlshortener.repository.UrlRepository;
import com.adarsh.urlshortener.util.Base62;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class UrlControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UrlRepository urlRepository;

    @Test
    void shortenUrl() throws Exception {
        ShortenRequest request = ShortenRequest.builder()
                .url("https://google.com")
                .build();

        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").exists());
    }

    @Test
    void duplicateUrlReturnsSameCode() throws Exception {
        ShortenRequest request = ShortenRequest.builder()
                .url("https://github.com")
                .build();

        String first = mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String second = mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn()
                .getResponse()
                .getContentAsString();

        ShortenResponse firstResponse = objectMapper.readValue(first, ShortenResponse.class);
        ShortenResponse secondResponse = objectMapper.readValue(second, ShortenResponse.class);

        org.junit.jupiter.api.Assertions.assertEquals(firstResponse.getCode(), secondResponse.getCode());
    }

    @Test
    void customAlias() throws Exception {
        ShortenRequest request = ShortenRequest.builder()
                .url("https://spring.io")
                .alias("spring")
                .build();

        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("spring"));
    }

    @Test
    void duplicateAlias() throws Exception {
        ShortenRequest request1 = ShortenRequest.builder()
                .url("https://abc.com")
                .alias("demo")
                .build();

        ShortenRequest request2 = ShortenRequest.builder()
                .url("https://xyz.com")
                .alias("demo")
                .build();

        mockMvc.perform(post("/shorten")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request1)));

        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidUrl() throws Exception {
        ShortenRequest request = ShortenRequest.builder()
                .url("hello")
                .build();

        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void redirect() throws Exception {
        ShortenRequest request = ShortenRequest.builder()
                .url("https://google.com")
                .alias("google")
                .build();

        mockMvc.perform(post("/shorten")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));

        mockMvc.perform(get("/google"))
                .andExpect(status().isMovedPermanently())
                .andExpect(header().string("Location", "https://google.com"));
    }

    @Test
    void unknownCode() throws Exception {
        mockMvc.perform(get("/random123"))
                .andExpect(status().isNotFound());
    }

    /* Edge Case Tests */

    @Test
    void aliasTooShort() throws Exception {
        ShortenRequest request = ShortenRequest.builder()
                .url("https://test.com")
                .alias("ab")
                .build();

        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aliasTooLong() throws Exception {
        ShortenRequest request = ShortenRequest.builder()
                .url("https://test.com")
                .alias("this-alias-is-definitely-longer-than-thirty-characters")
                .build();

        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aliasWithInvalidCharacters() throws Exception {
        ShortenRequest request = ShortenRequest.builder()
                .url("https://test.com")
                .alias("hello world")
                .build();

        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aliasBlankFallsBackToAutoGeneration() throws Exception {
        ShortenRequest request = ShortenRequest.builder()
                .url("https://fallback.com")
                .alias("")
                .build();

        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").exists())
                .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.not("")));
    }

    @Test
    void emptyOrNullUrl() throws Exception {
        ShortenRequest request = ShortenRequest.builder()
                .url("")
                .build();

        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    /* Concurrency Integration Tests */

    @Test
    void concurrentShortenRequestsSameUrl() throws Exception {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);

        List<Future<MvcResult>> futures = new ArrayList<>();
        String url = "https://concurrent-test-" + UUID.randomUUID() + ".com";
        ShortenRequest request = ShortenRequest.builder().url(url).build();
        String jsonRequest = objectMapper.writeValueAsString(request);

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                try {
                    latch.await(); // Wait for start signal
                    return mockMvc.perform(post("/shorten")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(jsonRequest))
                            .andReturn();
                } finally {
                    finishLatch.countDown();
                }
            }));
        }

        latch.countDown(); // Start all threads simultaneously
        finishLatch.await(5, TimeUnit.SECONDS); // Wait for completion

        String expectedCode = null;
        for (Future<MvcResult> future : futures) {
            MvcResult result = future.get();
            org.junit.jupiter.api.Assertions.assertEquals(200, result.getResponse().getStatus());
            String responseBody = result.getResponse().getContentAsString();
            ShortenResponse response = objectMapper.readValue(responseBody, ShortenResponse.class);
            if (expectedCode == null) {
                expectedCode = response.getCode();
            } else {
                org.junit.jupiter.api.Assertions.assertEquals(expectedCode, response.getCode());
            }
        }
        executor.shutdown();
    }

    @Test
    void concurrentShortenRequestsSameAlias() throws Exception {
        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);

        List<Future<MvcResult>> futures = new ArrayList<>();
        String alias = "alias-" + UUID.randomUUID().toString().substring(0, 8);

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            futures.add(executor.submit(() -> {
                try {
                    latch.await();
                    ShortenRequest request = ShortenRequest.builder()
                            .url("https://concurrent-alias-" + index + ".com")
                            .alias(alias)
                            .build();
                    return mockMvc.perform(post("/shorten")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request)))
                            .andReturn();
                } finally {
                    finishLatch.countDown();
                }
            }));
        }

        latch.countDown();
        finishLatch.await(5, TimeUnit.SECONDS);

        int successCount = 0;
        int conflictCount = 0;

        for (Future<MvcResult> future : futures) {
            int status = future.get().getResponse().getStatus();
            if (status == 200) {
                successCount++;
            } else if (status == 409) {
                conflictCount++;
            }
        }

        Assertions.assertEquals(1, successCount, "Exactly one thread should successfully register the alias");
        Assertions.assertEquals(threadCount - 1, conflictCount, "All other threads should fail with conflict status (409)");
        executor.shutdown();
    }

    @Test
    void nullUrl() throws Exception {
        ShortenRequest request = ShortenRequest.builder().build();

        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingRequestBody() throws Exception {
        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void redirectGeneratedCode() throws Exception {
        ShortenRequest request = ShortenRequest.builder()
                .url("https://example-" + UUID.randomUUID() + ".com")
                .build();

        String responseStr = mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        ShortenResponse response = objectMapper.readValue(responseStr, ShortenResponse.class);

        mockMvc.perform(get("/" + response.getCode()))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", request.getUrl()));
    }

    @Test
    void generatedCodeIsUrlSafe() throws Exception {
        ShortenRequest request = ShortenRequest.builder()
                .url("https://url-safe-" + UUID.randomUUID() + ".com")
                .build();

        String responseStr = mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        ShortenResponse response = objectMapper.readValue(responseStr, ShortenResponse.class);
        Assertions.assertTrue(response.getCode().matches("[A-Za-z0-9]+"));
    }

    @Test
    void base62Uniqueness() {
        Set<String> generatedCodes = new HashSet<>();
        for (long i = 1; i <= 100000; i++) {
            generatedCodes.add(Base62.encode(i));
        }
        Assertions.assertEquals(100000, generatedCodes.size());
    }

    @Test
    void veryLongUrl() throws Exception {
        String longPath = "a".repeat(1950);
        String longUrl = "https://example.com/" + longPath;

        ShortenRequest request = ShortenRequest.builder()
                .url(longUrl)
                .build();

        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").exists());
    }

    @Test
    void maxAliasLengthBoundary() throws Exception {
        String alias = "a".repeat(30);
        ShortenRequest request = ShortenRequest.builder()
                .url("https://max-alias-" + UUID.randomUUID() + ".com")
                .alias(alias)
                .build();

        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(alias));
    }

    @Test
    void minAliasLengthBoundary() throws Exception {
        ShortenRequest request = ShortenRequest.builder()
                .url("https://min-alias-" + UUID.randomUUID() + ".com")
                .alias("abc")
                .build();

        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("abc"));
    }

    @Test
    void aliasMatchingExistingGeneratedCode() throws Exception {
        String existingCode = "gen" + UUID.randomUUID().toString().substring(0, 5);
        urlRepository.save(com.adarsh.urlshortener.entity.UrlMapping.builder()
                .originalUrl("https://gen-url-" + UUID.randomUUID() + ".com")
                .shortCode(existingCode)
                .build());

        ShortenRequest request = ShortenRequest.builder()
                .url("https://different-url-" + UUID.randomUUID() + ".com")
                .alias(existingCode)
                .build();

        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void existingUrlWithDifferentAlias() throws Exception {
        String url = "https://existing-url-" + UUID.randomUUID() + ".com";
        ShortenRequest request1 = ShortenRequest.builder()
                .url(url)
                .build();

        String responseStr1 = mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        ShortenResponse response1 = objectMapper.readValue(responseStr1, ShortenResponse.class);

        ShortenRequest request2 = ShortenRequest.builder()
                .url(url)
                .alias("diffalias")
                .build();

        String responseStr2 = mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        ShortenResponse response2 = objectMapper.readValue(responseStr2, ShortenResponse.class);

        Assertions.assertEquals(response1.getCode(), response2.getCode());
    }

    @Test
    void httpUrl() throws Exception {
        ShortenRequest request = ShortenRequest.builder()
                .url("http://example-" + UUID.randomUUID() + ".com")
                .build();

        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void httpsUrl() throws Exception {
        ShortenRequest request = ShortenRequest.builder()
                .url("https://example-" + UUID.randomUUID() + ".com")
                .build();

        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void invalidSchemeFtp() throws Exception {
        ShortenRequest request = ShortenRequest.builder()
                .url("ftp://example.com")
                .build();

        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void specialCharactersInAlias() throws Exception {
        String[] invalidAliases = {"alias/test", "alias?test", "alias#test", "alias%test"};
        for (String alias : invalidAliases) {
            ShortenRequest request = ShortenRequest.builder()
                    .url("https://special-chars-" + UUID.randomUUID() + ".com")
                    .alias(alias)
                    .build();

            mockMvc.perform(post("/shorten")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void whitespaceAroundUrl() throws Exception {
        ShortenRequest request = ShortenRequest.builder()
                .url("  https://example.com  ")
                .build();

        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void caseSensitiveAlias() throws Exception {
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 5);
        ShortenRequest request1 = ShortenRequest.builder()
                .url("https://case1-" + UUID.randomUUID() + ".com")
                .alias("abc" + uniqueSuffix)
                .build();

        ShortenRequest request2 = ShortenRequest.builder()
                .url("https://case2-" + UUID.randomUUID() + ".com")
                .alias("ABC" + uniqueSuffix)
                .build();

        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isOk());
    }

    @Test
    void responseContentType() throws Exception {
        ShortenRequest request = ShortenRequest.builder()
                .url("https://content-type-" + UUID.randomUUID() + ".com")
                .build();

        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    void redirectLocationHeader() throws Exception {
        String url = "https://location-hdr-" + UUID.randomUUID() + ".com";
        ShortenRequest request = ShortenRequest.builder()
                .url(url)
                .build();

        String responseStr = mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        ShortenResponse response = objectMapper.readValue(responseStr, ShortenResponse.class);

        mockMvc.perform(get("/" + response.getCode()))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().exists("Location"))
                .andExpect(header().string("Location", url));
    }

    @Test
    void databaseRecordCreation() throws Exception {
        String url = "https://db-record-" + UUID.randomUUID() + ".com";
        ShortenRequest request = ShortenRequest.builder()
                .url(url)
                .build();

        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        Assertions.assertTrue(urlRepository.findByOriginalUrl(url).isPresent());
    }

    @Test
    void duplicateUrlDatabaseCheck() throws Exception {
        String url = "https://db-dup-" + UUID.randomUUID() + ".com";
        ShortenRequest request = ShortenRequest.builder()
                .url(url)
                .build();

        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        Assertions.assertEquals(1, urlRepository.findAll().stream().filter(m -> url.equals(m.getOriginalUrl())).count());
    }

}
