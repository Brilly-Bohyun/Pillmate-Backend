package pillmate.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pillmate.backend.common.exception.BadRequestException;
import pillmate.backend.common.exception.NotFoundException;
import pillmate.backend.common.util.JwtTokenProvider;
import pillmate.backend.dto.member.CheckEmailRequest;
import pillmate.backend.dto.member.CheckPasswordRequest;
import pillmate.backend.dto.member.FindPasswordRequest;
import pillmate.backend.dto.member.FindPasswordResponse;
import pillmate.backend.dto.member.JwtTokenResponse;
import pillmate.backend.dto.member.LoginResponse;
import pillmate.backend.dto.member.LogoutResponse;
import pillmate.backend.dto.member.ModifyPasswordRequest;
import pillmate.backend.dto.member.MyHealthInfo;
import pillmate.backend.dto.member.MyMonthlyInfo;
import pillmate.backend.dto.member.SignUpRequest;
import pillmate.backend.entity.member.Member;
import pillmate.backend.entity.member.MemberType;
import pillmate.backend.entity.token.LogoutAccessToken;
import pillmate.backend.entity.token.RefreshToken;
import pillmate.backend.repository.MedicinePerMemberRepository;
import pillmate.backend.repository.MedicineRecordRepository;
import pillmate.backend.repository.MemberRepository;
import pillmate.backend.service.token.LogoutAccessTokenService;
import pillmate.backend.service.token.RefreshTokenService;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static pillmate.backend.common.exception.errorcode.ErrorCode.ALREADY_EXIST_USER;
import static pillmate.backend.common.exception.errorcode.ErrorCode.EXPIRED_TOKEN_VALID_TIME;
import static pillmate.backend.common.exception.errorcode.ErrorCode.MISMATCH_EMAIL;
import static pillmate.backend.common.exception.errorcode.ErrorCode.MISMATCH_PASSWORD;
import static pillmate.backend.common.exception.errorcode.ErrorCode.MISMATCH_TOKEN;
import static pillmate.backend.common.exception.errorcode.ErrorCode.NOT_DEFAULT_TYPE_USER;
import static pillmate.backend.common.exception.errorcode.ErrorCode.NOT_FOUND_USER;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MemberService {
    private final MemberRepository memberRepository;
    private final MedicineRecordRepository medicineRecordRepository;
    private final RefreshTokenService refreshTokenService;
    private final LogoutAccessTokenService logoutAccessTokenService;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

    private LocalDate START_DATE = LocalDate.now().withDayOfMonth(1);
    private LocalDate END_DATE = LocalDate.now().minusDays(1);

    /**
     * 사용자가 입력한 정보를 가지고 MemberRepository에 저장하는 메소드
     * @param signUpRequest 회원 가입 폼에서 입력한 정보
     * 이 때, MemberType은 프론트에서 이전에 백으로 부터 전달받은 값 (없다면 null)
     * @return 회원가입한 정보로 만든 토큰 값
     */
    @Transactional
    public JwtTokenResponse register(SignUpRequest signUpRequest) {
        validate(signUpRequest);

        String encodedPassword = getEncodedPassword(signUpRequest);
        Member member = signUpRequest.toEntity(encodedPassword);

        memberRepository.save(member);
        return jwtTokenProvider.generateToken(member);
    }

    /**
     * 로그인 하는 시점에 토큰을 생성해서 반환하는 메소드 (로그인을 하는 시점에 토큰이 생성된다)
     * @param email 사용자 이메일
     * @param password 사용자 비밀번호
     * @return 발급한 토큰 정보
     */
    @Transactional
    public LoginResponse issueToken(String email, String password) {
        Member member = findMemberByEmail(email);
        boolean matches = passwordEncoder.matches(password, member.getPassword());

        if (!member.getType().equals(MemberType.DEFAULT)) {
            throw new NotFoundException(NOT_DEFAULT_TYPE_USER);
        }

        if (!matches) {
            throw new NotFoundException(MISMATCH_PASSWORD);
        }

        JwtTokenResponse tokenInfo = jwtTokenProvider.generateToken(member);
        return LoginResponse.from(tokenInfo, member);
    }

    /**
     * 현재 사용자의 토큰을 만료시고 블랙리스트에 저장하는 메소드
     * @param accessToken 사용자의 accessToken
     * @return 현재 사용자의 PK
     */
    @Transactional
    public LogoutResponse expireToken(String accessToken) {
        String resolvedAccessToken = jwtTokenProvider.resolveToken(accessToken);
        Long memberId = jwtTokenProvider.parseToken(resolvedAccessToken);
        Long remainTime = jwtTokenProvider.getRemainTime(resolvedAccessToken);

        refreshTokenService.deleteByMemberId(memberId);
        logoutAccessTokenService.saveLogoutAccessToken(LogoutAccessToken.from(resolvedAccessToken, remainTime));

        // LogoutDB 가 과부화될 가능성 있음 => 토큰 유효기간이 만료되면 자동 삭제되므로 염려할 필요 X
        return LogoutResponse.builder()
                .memberId(memberId)
                .build();
    }

    /**
     * 이메일과 전화번호를 통해 알맞는 회원의 비밀번호를 임시 비밀번호로 수정 및 임시 비밀번호를 반환하는 메소드
     * @param findPasswordRequest 이메일, 비밀번호
     * @return 발급된 임시 비밀번호
     */
    @Transactional
    public FindPasswordResponse issueTemporaryPassword(FindPasswordRequest findPasswordRequest) {
        String email = findPasswordRequest.getEmail();

        Member member = findMemberByEmail(email);

        if (!member.getType().equals(MemberType.DEFAULT)) {
            throw new BadRequestException(NOT_DEFAULT_TYPE_USER);
        }

        String temporaryPassword = UUID.randomUUID().toString().substring(0, 8);
        String encode = passwordEncoder.encode(temporaryPassword);
        member.updatePassword(encode);

        return FindPasswordResponse.builder()
                .tempPassword(temporaryPassword)
                .build();
    }

    public Boolean checkPassword(Long memberId, CheckPasswordRequest checkPasswordRequest) {
        Member member = findMemberById(memberId);
        return passwordEncoder.matches(checkPasswordRequest.getPassword(), member.getPassword());
    }

    public Boolean checkEmail(Long memberId, CheckEmailRequest checkEmailRequest) {
        return memberRepository.findByEmail(checkEmailRequest.getEmail()).isPresent();
    }

    @Transactional
    public void modifyPassword(Long memberId, ModifyPasswordRequest modifyPasswordRequest) {
        String oldPassword = modifyPasswordRequest.getOldPassword();
        String newPassword = modifyPasswordRequest.getNewPassword();

        Member member = findMemberById(memberId);
        if (!passwordEncoder.matches(oldPassword, member.getPassword())) {
            throw new BadRequestException(MISMATCH_PASSWORD);
        }

        // 회원 비밀번호 수정
        String encode = passwordEncoder.encode(newPassword);
        member.updatePassword(encode);
    }

    /**
     * 사용자가 만료된 accessToken 과 만료되지 않은 refreshToken을 넘길 때 새로운 accessToken을 만들어 주는 메소드
     * RefreshToken의 유효기간을 확인 후, 토큰을 재발급해주는 메소드
     *
     * @param memberId 사용자 pk
     * @param refreshToken 사용자로부터 넘겨 받은 refreshToken
     * @return 새로운 accessToken 이 담긴 JwtTokenResponse 객체
     */
    @Transactional
    public LoginResponse reissueToken(Long memberId, String refreshToken) {
        Member member = findMemberById(memberId);
        RefreshToken redisRefreshToken = refreshTokenService.findByMemberId(member.getId());
        if (redisRefreshToken == null) {
            throw new NotFoundException(EXPIRED_TOKEN_VALID_TIME);
        }

        if (!refreshToken.equals(redisRefreshToken.getToken())) {
            throw new NotFoundException(MISMATCH_TOKEN);
        }

        /** Authorization 사용하여 패스워드 가져올 때 PROTECTED 되있으므로 DB에서 사용자 내역을 가져온다.
            String password = userDetails.getPassword();
            참고 : https://djunnni.gitbook.io/springboot/2019-11-30
            Member member = memberRepository.findById(currentEmail).get();
            String password = passwordEncoder.encode(member.getPassword());
         **/

        JwtTokenResponse tokenInfo = jwtTokenProvider.generateToken(member);
        return LoginResponse.from(tokenInfo, member);
    }

    public MyHealthInfo getHealthInfo(Long memberId) {
        Member member = findMemberById(memberId);
        return MyHealthInfo.builder()
                .diseases(member.getDiseases())
                .symptoms(member.getSymptoms())
                .build();
    }

    @Transactional
    public ResponseEntity<String> modifyHealthInfo(Long memberId, MyHealthInfo modifyHealthInfo) {
        findMemberById(memberId).updateHealthInfo(modifyHealthInfo.getDiseases(), modifyHealthInfo.getSymptoms());
        return ResponseEntity.ok("수정이 완료되었습니다.");
    }

    public MyMonthlyInfo getMonthlyInfo(Long memberId) {
        return MyMonthlyInfo.builder()
                .grade(validateGrade(getRate(memberId)))
                .takenDay(getTakenDay(memberId))
                .month(getDuration(memberId))
                .rate(getRate(memberId))
                .build();
    }

    private Integer getTakenDay(Long memberId) {
        return medicineRecordRepository.countEatenDates(memberId, START_DATE, END_DATE).size();
    }

    private Integer getDuration(Long memberId) {
        return Math.toIntExact(ChronoUnit.DAYS.between(findMemberById(memberId).getCreated(), LocalDate.now()) + 1);
    }

    private Integer getRate(Long memberId) {
        return (getTakenDay(memberId) / getDuration(memberId)) * 100;
    }

    private Member findMemberById(Long memberId) {
        return memberRepository.findById(memberId).orElseThrow(() -> new NotFoundException(NOT_FOUND_USER));
    }

    private Member findMemberByEmail(String email) {
        return memberRepository.findByEmail(email).orElseThrow(() -> new NotFoundException(MISMATCH_EMAIL));
    }

    private void validate(SignUpRequest signUpRequest) {
        if (memberRepository.existsByEmailAndType(signUpRequest.getEmail(), MemberType.DEFAULT)) {
            throw new BadRequestException(ALREADY_EXIST_USER);
        }
    }

    /**
     * 인코딩된 비밀번호를 발급해주는 메소드
     * (만약, 소셜 로그인인 경우 UUID를 통한 랜덤 문자열을 인코딩하여 반환)
     * @param signUpRequest 로그인 정보
     * @return 인코딩된 비밀번호
     */
    private String getEncodedPassword(SignUpRequest signUpRequest) {
        String password = signUpRequest.getPassword() == null ? UUID.randomUUID().toString() : signUpRequest.getPassword();
        return passwordEncoder.encode(password);
    }

    private String validateGrade(Integer rate) {
        if (rate >= 95) {
            return "매우 우수";
        } else if (rate >= 90) {
            return "우수";
        } else if (rate >= 70) {
            return "보통";
        } else if (rate >= 50) {
            return "나쁨";
        } else {
            return "매우 나쁨";
        }
    }
}
