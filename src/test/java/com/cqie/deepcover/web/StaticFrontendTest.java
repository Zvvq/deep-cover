package com.cqie.deepcover.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class StaticFrontendTest {

    private static final Pattern SCRIPT_ASSET = Pattern.compile("src=\"(\\./assets/[^\"]+\\.js)\"");
    private static final Pattern STYLE_ASSET = Pattern.compile("href=\"(\\./assets/[^\"]+\\.css)\"");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void servesVueApplicationShellAndViteAssets() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("index.html"));

        MvcResult indexResult = mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<div id=\"app\"></div>")))
                .andExpect(content().string(containsString("type=\"module\"")))
                .andReturn();

        String indexHtml = indexResult.getResponse().getContentAsString();
        String scriptPath = extractAssetPath(indexHtml, SCRIPT_ASSET);
        String stylePath = extractAssetPath(indexHtml, STYLE_ASSET);

        mockMvc.perform(get(scriptPath))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("javascript")));

        mockMvc.perform(get(stylePath))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/css")));
    }

    private static String extractAssetPath(String indexHtml, Pattern assetPattern) {
        Matcher matcher = assetPattern.matcher(indexHtml);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1).replace("./", "/");
    }
}