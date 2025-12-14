package org.example.agent.audio;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class SttBuffer {

    private final Deque<String> texts = new ArrayDeque<>();
    private final Deque<Long> times = new ArrayDeque<>();

    private final long windowMs; // 15초

    public SttBuffer(long windowMs) {
        this.windowMs = windowMs;
    }

    public void add(String stt){
        long now = System.currentTimeMillis();
        texts.addLast(stt);
        times.addLast(now);
        // times라는 큐안에 값이 비어있지않고 현재시간-times에 첫값 을 뺐을 때 windowMs(15s)보다 낮다면 값 제거
        // 현재 5초단위로 가져오는 유저의 text를 3개씩 저장해서 원활한 대화를 하기위한 용도
        while(!times.isEmpty() && now - times.peekFirst() > windowMs){
            texts.removeFirst();
            times.removeFirst();
        }
    }

    public List<String> getTexts(){
        return new ArrayList<>(texts);
    }
    public String getJoined(){
        return String.join(", ", texts);
    }


}
