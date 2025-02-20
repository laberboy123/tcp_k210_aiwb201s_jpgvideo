package com.example.myapplication;

import android.Manifest;
import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {
    private static final int SERVER_PORT = 2345; // 设置 TCP 监听端口
    private static final String TAG = "TCPServer";
    private ServerSocket serverSocket;
    private boolean isRunning = true; // 控制服务器运行状态
    private Socket clientSocket;
    private TextView tvServerStatus;
    private TextView tvClientMessage;
    private ImageView videoView;
    private static final int REQUEST_WIFI_PERMISSION = 1;
    private PrintWriter commandWriter;
    private volatile boolean isConnected = false;
    // 用于发送命令的标志
    private boolean isPressed = false;
    private final Handler handler = new Handler();
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private Button currentButton; // 记录当前按下的按钮
    // 全局变量保存当前显示的 Bitmap，确保旧对象能被回收
    private Bitmap currentBitmap = null;
    // 创建一个固定大小的线程池
    ExecutorService executorService = Executors.newFixedThreadPool(5);
    private volatile boolean isReceiving = false;


    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 获取 UI 控件的引用
        tvServerStatus = findViewById(R.id.tvServerStatus);
        TextView tvServerIP = findViewById(R.id.tvServerIP);

        tvClientMessage = findViewById(R.id.tvClientMessage);
        videoView = findViewById(R.id.video_view);

        Button btnUp = findViewById(R.id.btnUp);
        Button btnDown = findViewById(R.id.btnDown);
        Button btnLeft = findViewById(R.id.btnLeft);
        Button btnRight = findViewById(R.id.btnRight);

        @SuppressLint("UseSwitchCompatOrMaterialCode") Switch switchMode = findViewById(R.id.switch2);
        switchMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // 判断 Switch 是否被切换
            if (isChecked) {
                // Switch 被打开，发送 CHANGE_MODE 命令
                sendChangeModeCommand();
            } else {
                // Switch 被关闭时，你也可以决定是否发送命令，或者什么都不做
                 sendChangeModeCommand();  // 如果需要在关闭时也发送命令，取消注释此行
            }
        });

        // 设置按钮按下和松开的监听器
        setButtonPressListener(btnUp);
        setButtonPressListener(btnDown);
        setButtonPressListener(btnLeft);
        setButtonPressListener(btnRight);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_WIFI_STATE) != PackageManager.PERMISSION_GRANTED) {
            // 如果没有权限，动态请求权限
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_WIFI_STATE}, REQUEST_WIFI_PERMISSION);
        } else{
            // 显示服务器的 IP 和端口
            String serverIP = Utils.getLocalIpAddress(this);
            tvServerIP.setText("服务器信息：" + serverIP + ":" + SERVER_PORT);
        }

        // 启动 TCP 服务器
        startServer();
    }

    // 启动 TCP 服务器
    private void startServer() {
        isRunning = true;
        executorService.submit(() -> {
            try {
                serverSocket = new ServerSocket(SERVER_PORT);
                Log.d(TAG, "TCP 服务器已启动，监听端口：" + SERVER_PORT);
                updateStatus("服务器已启动，等待连接...");

                while (isRunning) {
                    clientSocket = serverSocket.accept(); // 等待客户端连接
                    Log.d(TAG, "客户端已连接：" + clientSocket.getInetAddress());
                    updateStatus("客户端已连接: " + clientSocket.getInetAddress());
                    isConnected = true;

                    // 保持连接不关闭
                    OutputStream outputStream = clientSocket.getOutputStream();
                    commandWriter = new PrintWriter(outputStream, true); // 保持输出流

                    // 读取客户端文本消息
//                    executorService.submit(() -> readClientMessage(clientSocket));

                    // 启动接收图像流
                    executorService.submit(() -> startReceivingImages(clientSocket));
                }
            } catch (Exception e) {
                Log.e(TAG, "服务器错误: " + e.getMessage());
                updateStatus("服务器错误: " + e.getMessage());
            }
        });
    }

    private void readClientMessage(Socket socket) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String message;
            while ((message = reader.readLine()) != null) {
                tvClientMessage.setText(message);
                if ("fire".equalsIgnoreCase(message)) {
                    sendFireCommand();
                }
//                updateClientMessage(message);
            }
        } catch (IOException e) {
            Log.e(TAG, "读取消息错误: " + e.getMessage());
        }
    }

    // 更新服务器状态（UI线程）
    private void updateStatus(final String message) {
        uiHandler.post(() -> tvServerStatus.setText(message));
    }

    // 更新客户端消息（UI线程）
    @SuppressLint("SetTextI18n")
    private void updateClientMessage(final String message) {
        uiHandler.post(() -> tvClientMessage.setText("客户端消息: " + message));
    }

    // 向客户端发送"fire"命令
    private void sendFireCommand() {
        if (clientSocket != null && !clientSocket.isClosed()) {
            try {
                // 通过 PrintWriter 向客户端发送"fire"字符串
                synchronized (commandWriter) {
                    commandWriter.println("fire");
                    Log.d(TAG, "已向客户端发送: fire");
                }
            } catch (Exception e) {
                Log.e(TAG, "发送命令失败：" + e.getMessage());
                handleDisconnection();
            }
        } else {
            Log.w(TAG, "客户端未连接，无法发送命令");
        }
    }



    private void startReceivingImages(Socket socket) {
        isReceiving = true;
        try {
            InputStream inputStream = socket.getInputStream();
            ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192]; // 增大缓冲区
            int bytesRead;

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                byteBuffer.write(buffer, 0, bytesRead);
                byte[] data = byteBuffer.toByteArray();

                // 检查是否接收到完整的 JPEG 图像
                if (isCompleteImage(data)) {
                    Log.d(TAG, "完整图像大小: " + data.length);

                    executorService.submit(() -> decodeAndDisplayImage(data));
                    byteBuffer.reset(); // 重置缓冲区
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "接收图像错误: " + e.getMessage());
        }
    }

    // 资源回收方法
    private void closeResources(Closeable... resources) {
        for (Closeable res : resources) {
            if (res != null) {
                try {
                    res.close();
                } catch (IOException e) {
                    Log.e(TAG, "关闭资源失败", e);
                }
            }
        }

        if (currentBitmap != null && !currentBitmap.isRecycled()) {
            currentBitmap.recycle();
            currentBitmap = null;
        }
    }

    // 检查 JPEG 完整性
    private boolean isCompleteImage(byte[] data) {
        if (data.length < 4) {
            return false;
        }
        byte[] startMarker = { (byte) 0xFF, (byte) 0xD8 };
        byte[] endMarker = { (byte) 0xFF, (byte) 0xD9 };
        return Arrays.equals(Arrays.copyOfRange(data, 0, 2), startMarker) &&
                Arrays.equals(Arrays.copyOfRange(data, data.length - 2, data.length), endMarker);
    }
    // 解码并显示图像
    private void decodeAndDisplayImage(final byte[] imageData) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        try {
            Bitmap bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.length, options);
            if (bitmap != null) {
                uiHandler.post(() -> {
                    // 回收旧Bitmap
                    if (currentBitmap != null && !currentBitmap.isRecycled()) {
                        currentBitmap.recycle();
                    }

                    videoView.setImageBitmap(bitmap);
                    currentBitmap = bitmap; // 保持引用
                });
            }
        } catch (IllegalArgumentException e) {
            // 处理 inBitmap 复用失败的情况
            options.inBitmap = null;
            // 避免递归调用，改为日志记录
            Log.e(TAG, "解码失败，重试参数调整后仍失败: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "解码错误: " + e.getMessage());
        }
    }

    // 用于执行命令的 Runnable
    private final Runnable commandRunnable = new Runnable() {
        @Override
        public void run() {
            if (isPressed && currentButton != null) {
                executorService.submit(() -> sendCommandContinuous(currentButton)); // 持续发送命令
                handler.postDelayed(this, 100); // 每隔 50ms 发送一次命令
            }
        }
    };

    // 设置按钮的按下和松开事件
    @SuppressLint("ClickableViewAccessibility")
    private void setButtonPressListener(Button button) {
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

    // 切换模式按钮逻辑
    private void sendChangeModeCommand(){
        if (clientSocket == null || clientSocket.isClosed()) {
            uiHandler.post(() -> Toast.makeText(this, "No client connected", Toast.LENGTH_SHORT).show());
            return;
        }
        String command = "CHANGE_MODE";
        executorService.submit(() -> {
            try {
                synchronized (commandWriter) {  // 确保写操作不会被中断
                    commandWriter.println(command);
                    Log.d(TAG, "命令已发送: " + command);
                }

            } catch (Exception e) {
                Log.e(TAG, "发送异常: " + e.getClass().getSimpleName());
                handleDisconnection();
            }
        });

    }


    // 持续发送命令的逻辑
    private void sendCommandContinuous(Button button) {
        if (clientSocket == null || clientSocket.isClosed()) {
            uiHandler.post(() -> Toast.makeText(this, "No client connected", Toast.LENGTH_SHORT).show());
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
                        Log.d(TAG, "命令已发送: " + command);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "发送异常: " + e.getClass().getSimpleName());
                    handleDisconnection();
                }
            }).start();
        }
    }

    // 处理权限请求结果
    @SuppressLint("SetTextI18n")
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_WIFI_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 权限被授予，获取 IP 地址
                String serverIP = Utils.getLocalIpAddress(this);
                TextView tvServerIP = findViewById(R.id.tvServerIP);
                tvServerIP.setText("IP: " + serverIP);
            } else {
                // 权限被拒绝，显示错误消息
                Toast.makeText(this, "Permission Denied: ACCESS_WIFI_STATE", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void handleDisconnection() {
        isConnected = false;
        try {
            if (clientSocket != null) clientSocket.close();
            if (commandWriter != null) commandWriter.close();
        } catch (IOException e) {
            Log.e(TAG, "关闭连接异常", e);
        }
    }

    // 停止服务器
    private void stopServer() {
        isRunning = false;
        isReceiving = false;
        closeResources(commandWriter, clientSocket, serverSocket);
        try {
            if (clientSocket != null) clientSocket.close();
            if (serverSocket != null) serverSocket.close();
            updateStatus("服务器已停止");
        } catch (IOException e) {
            Log.e(TAG, "停止服务器错误: " + e.getMessage());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopServer(); // 确保退出时释放资源
        executorService.shutdown();
        if (currentBitmap != null && !currentBitmap.isRecycled()) {
            currentBitmap.recycle();
            currentBitmap = null;
        }
    }
}
