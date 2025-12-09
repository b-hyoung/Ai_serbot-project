package org.example.agent.audio.evaluator;

/* 오디오 유효성 검사  */

import java.util.List;

/**
 * STT 결과가 "사람의 구조 요청일 가능성이 높은지"를 간단한 규칙으로 판별하는 휴리스틱.
 * - null, 공백, 너무 짧은 문자열, 노이즈 패턴 등은 false
 * - "살려", "도와", "구해", "여기", "사람 있어" 등의 단어가 포함되면 true
 * - STT가 "살 려 주세요" 같이 띄어쓰기로 쪼개져도 감지할 수 있도록 공백 제거 후 검사
 */
public class AudioHeuristic {

    /* 구조 요청으로 간주할 핵심 키워드들 */
    private static final List<String> DISTRESS_KEYWORDS = List.of(
            "살려" , "살려주세요",
            "도와" , "도와주세요",
            "구해" , "구해주세요",
            "여기" , "여기요",
            "사람" , "사람 있어",
            "죽겠" , "못 움직" , "숨이" , "숨막" , "위험" , "불" , "불났" , "여기","헬프",
            "help" , "으악" , "아악" , "으" , "악"
    );

    /* 실패에 대한 처리 */
    private static final List<String> NOISE_PATTERNS = List.of(
            "<noise>" , "<unk>" , "<unitelligible>",
            "음성 인식 실패" , "들리지" , "알 수 없음"
    );

    /*
     * STT 텍스트가 "사람의 구조 요청"으로 볼 수 있는지 여부.
     * true -> AUDIO_CONTACT/CONFIRED_CONTACT 전환의 강한 근거의 사용 가능.
     * */
    public boolean isHumanLikeSpeech(String stt){
        if(stt.length()<2){
            return false;
        }

        // 1. 기본 정리
        String s = stt.trim();
        if(s.isEmpty()) return false;

        // 2. 노이즈 패턴 제거 (STT 엔진이 넣는 메타 토큰 등)
        String lower = s.toLowerCase();
        for(String noise : NOISE_PATTERNS){
            if(lower.contains(noise)){
                return false;
            }
        }

        // 4. 반복 문자만 있는지 (예: "ㅋㅋㅋㅋ", "ㅠㅠㅠㅠ", "아아아아")
        //    같은 문자가 3번 이상 연속되면 노이즈로 본다.
        if (s.matches("(.)\\1{2,}")) {
            return false;
        }

        // 5. 특수문자/기호만 있는 경우 (예: "!!!", "???")
        if (s.replaceAll("[가-힣A-Za-z0-9\\s]", "").length() == s.length()) {
            return false;
        }

        // 6. 공백 제거한 버전도 만들어서, "살 려 주세요" 같은 케이스도 잡는다.
        String noSpace = s.replaceAll("\\s+", "");

        // 7. 구조 요청 키워드 포함 여부 검사
        for (String kw : DISTRESS_KEYWORDS) {
            if (s.contains(kw) || noSpace.contains(kw)) {
                return true;
            }
        }

        // 8. 키워드는 없지만, 사람이 말한 "문장"일 가능성이 꽤 있는 경우
        //    ex) "여기 연기가 너무 많아요", "앞이 안 보여요"
        //    → 길이가 어느 정도 이상이면 "사람 말"로는 인정하지만,
        //      구조 요청인지 애매하면 false로 두고 LLM 쪽에 맡기고 싶으면 이 부분은 주석 처리.
        if (s.length() >= 6 && containsHangul(s)) {
            // 사람 말일 가능성은 높지만, "구조 요청"으로 확정하고 싶지 않으면 false로 유지해도 됨.
            // 여기서는 "근처에 사람이 있다" 정도 판단에 쓰고 싶다면 true로 둔다.
            return true;
        }
        return false;
    }

    /** 한글이 최소 한 글자라도 포함돼 있으면 "사람 말" 가능성이 있다고 본다. */
    private boolean containsHangul(String s) {
        return s.chars().anyMatch(ch -> (ch >= 0xAC00 && ch <= 0xD7A3));
    }
}
