package pillmate.backend.dto.member;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import pillmate.backend.common.util.JwtTokenConst;
import pillmate.backend.entity.member.Member;

@AllArgsConstructor
@Builder
@Data
@NoArgsConstructor
public class JwtTokenResponse {
    private Long userId;
    private String name;
    private String grantType;
    private String accessToken;

    public static JwtTokenResponse from(String accessToken, Member member) {
        return JwtTokenResponse.builder()
                .userId(member.getId())
                .name(member.getName())
                .grantType(JwtTokenConst.TOKEN_PREFIX)
                .accessToken(accessToken)
                .build();
    }
}
