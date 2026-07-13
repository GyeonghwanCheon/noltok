package com.example.noltok.block;

import com.example.noltok.block.dto.request.BlockRequest;
import com.example.noltok.block.dto.response.BlockDeleteResponse;
import com.example.noltok.block.dto.response.BlockListResponse;
import com.example.noltok.block.dto.response.BlockResponse;
import com.example.noltok.global.config.SecurityConfig;
import com.example.noltok.global.jwt.JwtAuthenticationFilter;
import com.example.noltok.global.jwt.JwtProvider;
import com.example.noltok.support.ControllerTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// HTTP 계층 검증이 목적 — 비즈니스 로직 분기는 BlockServiceTest(Mockito)가 커버
@WebMvcTest(controllers = BlockController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtProvider.class})
class BlockControllerTest extends ControllerTestSupport {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockitoBean
    private BlockService blockService;

    @Test
    void 차단_인증_토큰_없이_요청하면_401을_응답한다() throws Exception {
        mockMvc.perform(post("/api/v1/blocks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BlockRequest("상대닉네임"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 차단_정상_요청시_201을_응답한다() throws Exception {
        // given
        BlockRequest request = new BlockRequest("상대닉네임");
        given(blockService.blockUser(anyLong(), any(BlockRequest.class)))
                .willReturn(new BlockResponse(1L, 2L, "상대닉네임", LocalDate.now()));

        // when & then
        mockMvc.perform(post("/api/v1/blocks")
                        .header("Authorization", bearerToken(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.blockedNickname").value("상대닉네임"));
    }

    @Test
    void 차단_닉네임이_비어있으면_400을_응답한다() throws Exception {
        // given: @NotBlank 검증 실패 유도
        BlockRequest request = new BlockRequest("");

        // when & then
        mockMvc.perform(post("/api/v1/blocks")
                        .header("Authorization", bearerToken(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 차단목록조회_정상_요청시_200을_응답한다() throws Exception {
        // given
        given(blockService.getBlocks(1L)).willReturn(BlockListResponse.of(List.of()));

        // when & then
        mockMvc.perform(get("/api/v1/blocks")
                        .header("Authorization", bearerToken(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void 차단해제_정상_요청시_200을_응답한다() throws Exception {
        // given
        given(blockService.unblockUser(1L, 10L))
                .willReturn(BlockDeleteResponse.of(10L, "상대닉네임"));

        // when & then
        mockMvc.perform(delete("/api/v1/blocks/10")
                        .header("Authorization", bearerToken(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.blockId").value(10));
    }
}
