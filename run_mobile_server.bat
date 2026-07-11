@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo.
echo 魔裁文本框手机网页服务
echo.
echo 1. 让手机和这台电脑连接同一个 Wi-Fi
echo 2. 在电脑上查看本机 IP：ipconfig
echo 3. 手机浏览器打开：http://电脑IP:8000/mobile.html
echo.
echo 如果 Windows 防火墙弹窗，请允许 Python 访问专用网络。
echo 按 Ctrl+C 可以关闭服务。
echo.
python -m http.server 8000 --bind 0.0.0.0
pause
