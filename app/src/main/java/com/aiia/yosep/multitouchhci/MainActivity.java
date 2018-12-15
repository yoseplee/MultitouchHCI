package com.aiia.yosep.multitouchhci;

import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.TextView;

import java.util.Locale;

import static android.media.AudioRecord.ERROR;

/*
Author: Yosep Lee
HCI 과제로 제작중. - 싱글터치
추후 Vibrator는 Thread로 제어되어야 할 필요가 있을 수 있음.
매핑 테이블과 입력된 코드와 일치 완료.

2018.12.04.
ISSUE#1: 입력하다 중간에 멈추게 되면? 타이머로 재고 있다가 refresh를 시켜주는 등 적용이 필요함.
ISSUE#2: 중간에 입력 취소 하고 싶으면?

2018.12.15. 주석 달기 완료.

추후 의사소통을 위한 인터페이스로 발전할 수 있지 않을까?
현재는 접촉수화만을 이용하고 있는데,
입력 방법론 등을 연구하여서 일반인과 의사소통 할 수 있는 인터페이스로 발전 가능하다.
관건은 이중감각장애자가 직관적으로 입력할 수 있는 약속들을 개발하는 것.
 */

public class MainActivity extends AppCompatActivity {

    //터치 사이의 인터벌을 재기 위한 변수
    long start = 0;
    long end = 0;
    long pointerStart = 0;
    long pointerEnd = 0;
    long interval = 0;
    long pointerInterval = 0;

    //진동 관련 변수 제어
    final int MAX_VIBTIME = 3000;
    final int thresold = 100; //short 미만을 걸러내기 위한 수치.
    final long wait = 150;

    //진동을 저장하기 위한 버퍼 변수와 포인터 플래그
    int[] buf;
    int bufPos = 0;
    long[] initBuf = {2000, 100, 150, 100, 2000};

    // 3개까지의 멀티터치를 다루기 위한 배열
    int id[] = new int[3];
    int x[] = new int[3];
    int y[] = new int[3];
    //멀티 터치가 발생했을 때 진동 패턴 설정. 배열의 의미는 {휴식, 진동, 휴식, 진동 ...}
    long[] multiPattern = {80, 50, 80, 50, 80, 50, 80, 50, 80, 50, 2000};
    String result;

    //진동, tts 및 UI 객체
    Vibrator vibrator;
    TextView textViewCenter;
    TextView textViewTop;
    private TextToSpeech tts;

    //안드로이드의 한 액티비티(화면)이 최초 실행될 때 실행되는 함수이다. 각종 값을 초기화 한다.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        buf = new int[3];

        //vibrator instance
        vibrator = (Vibrator)getSystemService(VIBRATOR_SERVICE);

        //UI관련 객체 초기화
        textViewCenter = (TextView)findViewById(R.id.textView1);
        textViewTop = (TextView)findViewById(R.id.textView2);

        // TTS를 생성하고 OnInitListener로 초기화 한다.
        tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if(status != ERROR) {
                    // 언어를 선택한다.
                    tts.setLanguage(Locale.KOREAN);
                }
            }
        });

        //대기 상태의 진동을 무제한 수행한다. repeat 0-> inf, 1-> once
        vibrator.vibrate(initBuf, 0);
    }

    /*
    디스플레이에 터치 이벤트가 발생했을 때 처리 구현.
    터치 액션을 받아와서, 눌렀을 때, 뗐을 때를 구분하여 처리한다.
    눌렀을 때: MotionEvent.ACTION_DOWN
    뗐을 때: MotionEvent.ACTION_UP

    멀티터치의 경우 신경써야 하는 처리 방식은 아래과 같다.
    반드시 이벤트의 발생은, ACTION DOWN -> ACTION_POINTER_DOWN -> ACTION_POINTER_UP -> ACTION_UP
    순서로 발생한다.
    즉 반드시 싱글 터치를 경유할 수 밖에 없다는 말이므로 이에 대한 처리가 필요하다.
    이번 구현에서는 싱글터치의 인터벌과 멀티터치 구간에서의 인터벌을 구하고 그 차이를 비교하여
    싱글 터치와 멀티 터치를 구분한다.
    아래 switch-case문 내부 삽입되어 있는 조건문의 interval, pointerInterval 비교가 그것이다.
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int pointer_count = event.getPointerCount(); //현재 터치 발생한 포인트 수를 얻는다.
        if(pointer_count > 3) pointer_count = 3; //4개 이상의 포인트를 터치했더라도 3개까지만 처리를 한다.

        switch(event.getAction() & MotionEvent.ACTION_MASK) {

            case MotionEvent.ACTION_DOWN: //한 개 포인트에 대한 DOWN을 얻을 때.(눌렀을 때)
                start = System.currentTimeMillis();
                result = "싱글터치 : \n";
                id[0] = event.getPointerId(0); //터치한 순간부터 부여되는 포인트 고유번호 받아오기.
                x[0] = (int) (event.getX());              // 그 포인트의 x값 받아오기
                y[0] = (int) (event.getY());              // 그 포인트의 y값 받아오기
                result += "("+x[0]+","+y[0]+")";
                break;

            case MotionEvent.ACTION_UP: //한 개 포인트에 대한 UP을 얻을 때.(뗐을 때)
                end = System.currentTimeMillis();
                interval = end - start;
                Log.e("**interal", "@ACTION_UPInterval:: " + interval);
                Log.e("**interal", "@ACTION_UPPointer Interval:: " + pointerInterval);
                if(pointerInterval < 10 && interval > thresold ) {
                    //single touch
                    result = "싱글터치 MOVE:\n";
                    //buf to 1
                    buf[bufPos++] = 1;

                    //vibrate
                    vibrator.vibrate(300);
                }
                interval = 0;
                pointerInterval = 0;
                break;

            case MotionEvent.ACTION_POINTER_DOWN: //두 개 이상의 포인트에 대한 DOWN을 얻을 때(눌렀을 때)
                pointerStart = System.currentTimeMillis();
                result = "멀티터치 :\n";
                for(int i = 0; i < pointer_count; i++) {
                    id[i] = event.getPointerId(i); //터치한 순간부터 부여되는 포인트 고유번호.
                    x[i] = (int) (event.getX(i));
                    y[i] = (int) (event.getY(i));
                    result += "id[" + id[i] + "] ("+x[i]+","+y[i]+")\n";
                }
                break;
            case MotionEvent.ACTION_POINTER_UP: //멀티터치 떼기
                pointerEnd = System.currentTimeMillis();
                pointerInterval = pointerEnd - pointerStart;
                /*
                //디버깅을 위해 로그 찍어보기
                Log.e("**interal", "@ACTION_POINTER_UPInterval:: " + interval);
                Log.e("**interal", "@ACTION_POINTER_UPPointer Interval:: " + pointerInterval);
                */
                //일정 길이 이상의 진동만 인식한다.
                if(pointerInterval > thresold) {
                    //multi touch
                    result = "멀티터치 MOVE:\n";
                    //buf to 2
                    buf[bufPos++] = 2;

                    //vibrate
                    vibrator.vibrate(multiPattern, -1);
                }
                break;


            case MotionEvent.ACTION_MOVE:   //터치 후 움직일 때... 사실 터치만 해도 MOVE 이벤트가 발생하곤 한다.(독립적)
                //pointer interval이 0이면 무조건 싱글터치
                for(int i = 0; i < pointer_count; i++) {
                    id[i] = event.getPointerId(i);
                    x[i] = (int) (event.getX(i));
                    y[i] = (int) (event.getY(i));
                    result += "id[" + id[i] + "] ("+x[i]+","+y[i]+")\n";
                }
                break;
        }

        textViewCenter.setText(result);

        //if buf full, do tts
        if(bufPos > 2) {
            String toSpeech = convertToText();

            //TTS
            tts.setPitch(1f);         // 음성 톤 설정
            tts.setSpeechRate(1f);    // 읽는 속도 설정
            tts.speak(toSpeech,TextToSpeech.QUEUE_FLUSH, null);  //tts로 스트링 읽기

            clearBuf();

            //vib
            vibrator.vibrate(initBuf, 0);
            return false;
        }
        return super.onTouchEvent(event);
    }
    public void clearBuf() {
        for(int i = 0; i < bufPos; i++) {
            buf[i] = 0;
        }
        bufPos = 0;
    }

    /*
        입력된 신호와 그 의미를 매핑한다.
        매핑 테이블은 구현 아티팩트를 참고할 것.
        1과 2는 각각 short term vibration과 long term vibration을 의미한다.
     */
    public String convertToText() {
        /*
        normbuf에 들어있는 터치 값을 텍스트로 변환환다. 111,112, 등 ...
         */
        String code = getCodeBuf();
        Log.e("**code", code);
        String result;
        switch(code) {
            case "111":
                result = "이요셉 간병인을 불러주세요.";
                break;
            case "112":
                result = "전 시청각중복 장애인입니다.";
                break;
            case "121":
                result = "죄송합니다.";
                break;
            case "211":
                result = "가까운 병원으로 데려가 주세요.";
                break;
            case "122":
                result = "도와주세요!";
                break;
            case "212":
                result = "감사합니다.";
                break;
            case "221":
                result = "가까운 경찰서로 데려가 주세요.";
                break;
            case "222":
                result = "010 8891 5420 으로 연락 부탁드립니다.";
                break;
            default:
                result = "에러 에러";
                break;
        }
        textViewTop.setText(""+code+"||"+result);
        return result;
    }

    public String getCodeBuf() {
        String result = "";
        for(int i = 0; i < bufPos; i++) {
            switch (buf[i]) {
                case 0:
                    break;
                case 1:
                    result += "1";
                    break;
                case 2:
                    result += "2";
                    break;
            }
        }
        return result;
    }

}
