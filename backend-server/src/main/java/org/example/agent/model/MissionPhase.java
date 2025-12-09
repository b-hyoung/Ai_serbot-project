package org.example.agent.model;

/* 로봇의 현재 미션 상태 */
public enum MissionPhase {
    SEARCHING, // 탐색 중
    AUDIO_CONTACT, // 소리로 사람 가능성 발견
    CONFIRMED_CONTACT, // 센서 / 카메라를 통해 사람 확인
    RESCUE_GUIDE // 구조 / 유도 
}
