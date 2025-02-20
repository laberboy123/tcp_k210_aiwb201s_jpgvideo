# K210 + AI-WB2-01S 与 APP 建立 TCP 连接实现 JPG 图传
**遇到 k210 的内存不足问题可以通过烧录更小的固件解决：**
1. 在识别过程中压缩图像，压缩程度越高，画面越不清晰，但图片的字节数更少
2. 通过串口发送压缩后的 jpg 字节流，发送后可以通过 python 脚本解析字节流显示对应 jpg
3. 将 jpg 字节流发送给串口连接的 aiwb2，aiwb2 tcp 透传发送数据中无 jpg 头尾表示，设置同样的波特率解决该问题
4. 提高波特率（115200 到 921600）加快视频流帧率

**远程控制小车：**
1. aiwb2打开 tcp 服务器，使用 原生tcp socket连接可以收发消息
2. 使用uniapp 作为 tcp客户端连接，但uniapp是通过websocket连接 tcp服务器，aiwb2貌似不支持 websocket
3. aiwb2作为 tcp 服务器时不能开启透传模式（每次传输数据需要携带AT指令）
4. 总结：需要 APP 端作为 TCP 服务器

**Android Studio 项目实现 TCP 服务器**
1. 根据局域网获取 IP 地址，在 Manifest 中添加网络权限请求，使用 wifiManager 库获取 int 类型的 IP 地址，最后转化为点分十进制字符串
2. 使用 ServerSocket 库以及自定义的端口号打开 TCP 服务器，需要通过一个输出流来保持连接不关闭，开启网络服务的 APP 线程数量会激增，使用线程池进行限制
3. TCP 客户端根据 IP:Port 进行连接
4. 确认 JPG 数据是正确的，将字节流转为 bitmap 通过 videoView组件显示

**APP 中实现按钮组件，向 aiwb2 发送消息**
