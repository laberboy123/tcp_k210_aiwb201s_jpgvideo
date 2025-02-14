package com.example.myapplication;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import java.io.PrintWriter;
import java.net.Socket;

public class Control {
    private Activity activity;
    private Socket clientSocket;
    private PrintWriter commandWriter;
    private boolean isPressed = false;
    private Handler handler;
    private Button currentButton;

    // 用于执行命令的 Runnable
    private final Runnable commandRunnable = new Runnable() {
        @Override
        public void run() {
            if (isPressed && currentButton != null) {
                sendCommandContinuous(currentButton); // 持续发送命令
                handler.postDelayed(this, 100); // 每隔 100ms 发送一次命令
            }
        }
    };

    public Control(Activity activity, Socket clientSocket, PrintWriter commandWriter) {
        this.activity = activity;
        this.clientSocket = clientSocket;
        this.commandWriter = commandWriter;
        this.handler = new Handler();
    }

    // 设置按钮的按下和松开事件
    @SuppressLint("ClickableViewAccessibility")
    public void setButtonPressListener(Button button) {
        button.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    // 按下按钮时开始发送命令
                    if (!isPressed) {  // 防止重复启动
                        isPressed = true;
                        currentButton = button;
                        handler.post(commandRunnable); // 开始定时发送命令
                    }
                    break;

                case MotionEvent.ACTION_UP:
                    // 松开按钮时停止发送命令
                    if (isPressed) {
                        isPressed = false;
                        handler.removeCallbacks(commandRunnable); // 停止定时发送命令
                        currentButton = null;  // 清空当前按钮
                    }
                    break;
            }
            return false; // 返回 false 以确保触发点击事件
        });
    }

    // 持续发送命令的逻辑
    private void sendCommandContinuous(Button button) {
        if (clientSocket == null || clientSocket.isClosed()) {
            Toast.makeText(activity, "No client connected", Toast.LENGTH_SHORT).show();
            return;
        }

        String command;
        if (button.getId() == R.id.btnUp) {
            command = "MOVE_FORWARD";
        } else if (button.getId() == R.id.btnDown) {
            command = "MOVE_BACKWARD";
        } else if (button.getId() == R.id.btnLeft) {
            command = "TURN_LEFT";
        } else if (button.getId() == R.id.btnRight) {
            command = "TURN_RIGHT";
        } else {
            command = "";
        }

        if (!command.isEmpty()) {
            new Thread(() -> {
                try {
                    synchronized (commandWriter) {
                        commandWriter.println(command);
                        System.out.println("命令已发送: " + command);
                    }
                } catch (Exception e) {
                    System.err.println("发送异常: " + e.getClass().getSimpleName());
                }
            }).start();
        }
    }
}