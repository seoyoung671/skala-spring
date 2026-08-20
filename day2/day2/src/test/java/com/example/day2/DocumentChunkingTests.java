package com.example.day2;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.core.io.ClassPathResource;

class DocumentChunkingTests {

    @ParameterizedTest
    @ValueSource(strings = {"return-policy.md", "shipping-policy.md", "membership.md"})
    void eachDocumentIsSplitIntoAtLeastTwoChunks(String filename) {
        List<Document> chunks = TokenTextSplitter.builder()
                .withChunkSize(250)
                .withMinChunkSizeChars(120)
                .withKeepSeparator(true)
                .build()
                .apply(new TextReader(new ClassPathResource("lab2-docs/" + filename)).get());

        assertThat(chunks)
                .as("%s chunks", filename)
                .hasSizeGreaterThanOrEqualTo(2);
    }
}
