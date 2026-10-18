import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:math';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:mobile_scanner/mobile_scanner.dart';
import 'package:qr_flutter/qr_flutter.dart';
import 'package:flutter/services.dart';
import 'package:image_picker/image_picker.dart';
import 'package:path_provider/path_provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'update_service.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const AlmasApp());
}

class AlmasApp extends StatelessWidget {
  const AlmasApp({super.key});

  @override
  Widget build(BuildContext context) => MaterialApp(
        title: 'ALMAS CONNECT',
        debugShowCheckedModeBanner: false,
        theme: ThemeData(
          brightness: Brightness.dark,
          colorSchemeSeed: Colors.tealAccent,
          useMaterial3: true,
        ),
        home: const HomePage(),
      );
}

class HomePage extends StatefulWidget {
  const HomePage({super.key});

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  static const int port = 8765;
  final _ipController = TextEditingController();
  final _codeController = TextEditingController();
  final _textController = TextEditingController();
  final _random = Random.secure();
  final _approvedTokens = <String>{};
  final _failedPairs = <String, List<DateTime>>{};
  final _events = <String>[];
  HttpServer? _server;
  String _localIp = 'Анықталуда';
  List<String> _localIps = [];
  String _myCode = '';
  String _myToken = '';
  String? _remoteIp;
  String? _remoteToken;
  String _remoteName = '';
  String _status = 'Сервер іске қосылуда...';
  bool _busy = false;
  double _progress = 0;

  final _updater = AlmasUpdater();
  Timer? _updateTimer;
  AlmasRelease? _latestUpdate;
  File? _downloadedUpdate;
  bool _checkingUpdates = false;
  bool _downloadingUpdate = false;
  bool _autoWifiUpdates = false;
  double _updateProgress = 0;
  String _updateState = 'Жаңартулар тексерілмеген';


  String get _deviceName => Platform.isWindows ? 'Windows ноутбук' : 'Android телефон';
  bool get _connected => _remoteIp != null && _remoteToken != null;

  @override
  void initState() {
    super.initState();
    _myCode = (100000 + _random.nextInt(900000)).toString();
    _myToken = _makeToken();
    _approvedTokens.add(_myToken);
    unawaited(_startServer());
    unawaited(_initializeUpdates());
    _updateTimer = Timer.periodic(const Duration(hours: 12), (_) {
      unawaited(_checkUpdates(silent: true));
    });
  }

  Future<void> _initializeUpdates() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      if (mounted) setState(() {
        _autoWifiUpdates = prefs.getBool('auto_wifi_updates') ?? false;
      });
    } catch (_) { /* App still allows manual checks. */ }
    await _checkUpdates(silent: true);
  }

  Future<void> _setAutoWifiUpdates(bool enabled) async {
    setState(() => _autoWifiUpdates = enabled);
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setBool('auto_wifi_updates', enabled);
    } catch (e) {
      _log('Баптауды сақтау мүмкін болмады: $e');
    }
    if (enabled && _latestUpdate != null && !_downloadingUpdate) {
      if (await _updater.canAutoDownload()) unawaited(_downloadUpdate());
    }
  }

  Future<void> _checkUpdates({bool silent = false}) async {
    if (_checkingUpdates || _downloadingUpdate || !mounted) return;
    setState(() => _checkingUpdates = true);
    try {
      final newer = await _updater.check();
      if (!mounted) return;
      setState(() {
        _latestUpdate = newer;
        _downloadedUpdate = null;
        _updateState = newer == null
            ? 'Жаңа жарияланған нұсқа жоқ (v$almasVersion)'
            : 'Жаңа нұсқа: v${newer.version} (${(newer.size / (1024 * 1024)).toStringAsFixed(1)} МБ)';
      });
      if (newer != null && _autoWifiUpdates &&
          await _updater.canAutoDownload()) {
        unawaited(_downloadUpdate());
      }
    } catch (e) {
      if (!mounted) return;
      setState(() => _updateState = 'Тексеру мүмкін болмады (интернет жоқ болуы мүмкін)');
      if (!silent) _log('Жаңартуды тексеру қатесі: $e');
    } finally {
      if (mounted) setState(() => _checkingUpdates = false);
    }
  }

  Future<void> _downloadUpdate() async {
    final release = _latestUpdate;
    if (release == null || _downloadingUpdate || !mounted) return;
    setState(() {
      _downloadingUpdate = true;
      _updateProgress = 0;
      _updateState = 'Жаңарту жүктелуде...';
    });
    var lastRefresh = DateTime.now();
    try {
      final file = await _updater.download(
        release,
        onProgress: (loaded, total) {
          if (!mounted) return;
          final now = DateTime.now();
          if (now.difference(lastRefresh).inMilliseconds < 300 && loaded != total) return;
          lastRefresh = now;
          setState(() => _updateProgress = total == 0 ? 0 : loaded / total);
        },
      );
      if (!mounted) return;
      setState(() {
        _downloadedUpdate = file;
        _updateState = 'v${release.version} жүктелді және SHA-256 тексерілді';
      });
      await _offerInstall();
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _updateState = 'Жүктеу тоқтады. Кейін сол жерден жалғастыруға болады.';
      });
      _log('Жаңарту: $e');
    } finally {
      if (mounted) setState(() => _downloadingUpdate = false);
    }
  }

  Future<void> _offerInstall() async {
    final file = _downloadedUpdate;
    if (file == null || !mounted) return;
    final approved = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Жаңартуды орнату керек пе?'),
        content: Text(Platform.isAndroid
            ? 'Android орнатуға рұқсат сұрайды. Қолданба деректерін сақтап қалу үшін APK бұрынғы нұсқамен бірдей кілтпен қол қойылуы керек.'
            : 'Windows қолданбасы жабылып, жаңартылған EXE файлдарына ауыстырылады. Бағдарламаның ZIP қалтасына жазуға рұқсат қажет.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false),
              child: const Text('Кейін')),
          FilledButton(onPressed: () => Navigator.pop(ctx, true),
              child: const Text('Орнату')),
        ],
      ),
    );
    if (approved != true) return;
    try {
      if (Platform.isAndroid) {
        await _updater.installAndroid(file);
      } else if (Platform.isWindows) {
        await _updater.installWindows(file);
      }
    } catch (e) {
      _log('Орнату іске қосылмады: $e');
    }
  }


  String _makeToken() => List<int>.generate(32, (_) => _random.nextInt(256))
      .map((n) => n.toRadixString(16).padLeft(2, '0'))
      .join();

  void _log(String message) {
    if (!mounted) return;
    setState(() {
      _events.insert(0, '${DateTime.now().hour.toString().padLeft(2, '0')}:${DateTime.now().minute.toString().padLeft(2, '0')}  $message');
      if (_events.length > 25) _events.removeLast();
    });
  }

  Future<void> _startServer() async {
    try {
      _server = await HttpServer.bind(InternetAddress.anyIPv4, port);
      _server!.listen((request) => unawaited(_handleRequest(request)));
      final interfaces = await NetworkInterface.list(type: InternetAddressType.IPv4);
      final addresses = interfaces.expand((interface) => interface.addresses).map((a) => a.address).toList();
      final preferred = addresses.where((a) => a.startsWith('192.168.') || a.startsWith('10.') || RegExp(r'^172\.(1[6-9]|2[0-9]|3[01])\.').hasMatch(a)).toList();
      if (!mounted) return;
      setState(() {
        _localIps = preferred.toSet().toList()..sort();
        _localIp = _localIps.isNotEmpty ? _localIps.first : 'IP табылмады';
        _status = 'Қосылуға дайын';
      });
    } catch (e) {
      if (mounted) setState(() => _status = 'Сервер қатесі: $e');
    }
  }

  Future<void> _respond(HttpRequest request, int status, Map<String, Object?> body) async {
    request.response.statusCode = status;
    request.response.headers.contentType = ContentType.json;
    request.response.write(jsonEncode(body));
    await request.response.close();
  }

  Future<Map<String, dynamic>> _readJson(HttpRequest request) async {
    if (request.headers.contentLength > 16384) throw const FormatException('Сұрау тым үлкен');
    final bytes = await request.fold<List<int>>(<int>[], (a, b) {
      if (a.length + b.length > 16384) throw const FormatException('Сұрау тым үлкен');
      return a..addAll(b);
    });
    final result = jsonDecode(utf8.decode(bytes));
    if (result is! Map<String, dynamic>) throw const FormatException('JSON нысаны қажет');
    return result;
  }

  bool _authenticated(HttpRequest request) => _approvedTokens.contains(request.headers.value('x-almas-token'));

  Future<void> _handleRequest(HttpRequest request) async {
    try {
      if (request.method == 'POST' && request.uri.path == '/pair') {
        final addr = request.connectionInfo?.remoteAddress.address ?? '?';
        final now = DateTime.now();
        final attempts = _failedPairs.putIfAbsent(addr, () => <DateTime>[]);
        attempts.removeWhere((dt) => now.difference(dt).inMinutes >= 5);
        if (attempts.length >= 5) {
          await _respond(request, HttpStatus.tooManyRequests, {'error': 'Көп қате код. 5 минуттан кейін қайталаңыз'});
          return;
        }
        final data = await _readJson(request);
        final code = data['code'];
        final callbackToken = data['callbackToken'];
        if (code != _myCode || callbackToken is! String || !RegExp(r'^[a-f0-9]{64}$').hasMatch(callbackToken)) {
          attempts.add(now);
          await _respond(request, HttpStatus.forbidden, {'error': 'Код дұрыс емес'});
          return;
        }
        final device = (data['device'] ?? 'Құрылғы').toString();
        if (!mounted) {
          await _respond(request, HttpStatus.serviceUnavailable, {'error': 'Қолданба жабық'});
          return;
        }
        setState(() {
          _remoteIp = addr;
          _remoteToken = callbackToken;
          _remoteName = device;
        });
        _log('$device жұпталды ($addr)');
        await _respond(request, HttpStatus.ok, {'token': _myToken, 'device': _deviceName});
        return;
      }
      if (!_authenticated(request)) {
        await _respond(request, HttpStatus.unauthorized, {'error': 'Жұптау қажет'});
        return;
      }
      if (request.method == 'POST' && request.uri.path == '/text') {
        final data = await _readJson(request);
        final value = data['text'];
        if (value is! String || value.length > 100000) {
          await _respond(request, HttpStatus.badRequest, {'error': 'Мәтін қатесі'});
          return;
        }
        await Clipboard.setData(ClipboardData(text: value));
        _log('Мәтін буферге көшірілді (${value.length} таңба)');
        await _respond(request, HttpStatus.ok, {'ok': true});
        return;
      }
      if (request.method == 'POST' && request.uri.path == '/upload') {
        final contentLength = request.headers.contentLength;
        if (contentLength < 0 || contentLength > 8 * 1024 * 1024 * 1024) {
          await _respond(request, HttpStatus.requestEntityTooLarge, {'error': 'Файл өлшемі жарамсыз'});
          return;
        }
        final rawName = request.uri.queryParameters['name'] ?? 'file';
        final safeName = rawName.replaceAll(RegExp(r'[\\/:*?"<>|\x00-\x1f]'), '_').replaceAll('..', '_');
        final name = safeName.isEmpty ? 'file' : safeName;
        final dir = Platform.isWindows
            ? (await getDownloadsDirectory() ?? await getApplicationDocumentsDirectory())
            : await getApplicationDocumentsDirectory();
        final targetDir = Directory('${dir.path}${Platform.pathSeparator}ALMAS CONNECT');
        await targetDir.create(recursive: true);
        final uniqueName = '${DateTime.now().millisecondsSinceEpoch}_$name';
        final file = File('${targetDir.path}${Platform.pathSeparator}$uniqueName');
        final part = File('${file.path}.part');
        try {
          final sink = part.openWrite();
          await sink.addStream(request);
          await sink.flush();
          await sink.close();
          if (await part.length() != contentLength) throw const FormatException('Файл толық келмеді');
          await part.rename(file.path);
          final mode = request.uri.queryParameters['mode'] ?? 'downloads';
          final kind = request.uri.queryParameters['kind'] ?? 'file';
          String clipboardNote = '';
          if (mode == 'clipboard' && Platform.isWindows) {
            clipboardNote = await _copyFileToWindowsClipboard(file, kind);
          }
          _log('Файл алынды: $name${clipboardNote.isEmpty ? '' : ' ($clipboardNote)'}');
          await _respond(request, HttpStatus.ok, {'ok': true, 'path': file.path, 'clipboard': clipboardNote});
        } catch (_) {
          if (await part.exists()) await part.delete();
          rethrow;
        }
        return;
      }
      await _respond(request, HttpStatus.notFound, {'error': 'Мекенжай табылмады'});
    } catch (e) {
      try {
        await _respond(request, HttpStatus.badRequest, {'error': e.toString()});
      } catch (_) { /* Client disconnected. */ }
    }
  }

  Future<String> _copyFileToWindowsClipboard(File file, String kind) async {
    final escaped = file.absolute.path.replaceAll("'", "''");
    const imports = 'Add-Type -AssemblyName System.Windows.Forms; ';
    final script = kind == 'photo'
        ? "$imports Add-Type -AssemblyName System.Drawing; \$img = [System.Drawing.Image]::FromFile('$escaped'); try { [System.Windows.Forms.Clipboard]::SetImage(\$img) } finally { \$img.Dispose() }"
        : "$imports \$c = New-Object System.Collections.Specialized.StringCollection; [void]\$c.Add('$escaped'); [System.Windows.Forms.Clipboard]::SetFileDropList(\$c)";
    final outcome = await Process.run('powershell.exe', ['-NoProfile', '-STA', '-Command', script]);
    return outcome.exitCode == 0 ? 'Буферге көшірілді' : 'Буферге қою мүмкін болмады';
  }

  bool _isLocalIp(String ip) {
    final address = InternetAddress.tryParse(ip);
    if (address == null || address.type != InternetAddressType.IPv4) return false;
    final b = address.rawAddress;
    return b[0] == 10 || (b[0] == 192 && b[1] == 168) ||
        (b[0] == 172 && b[1] >= 16 && b[1] <= 31);
  }

  String get _myPairingQr => Uri(
    scheme: 'almasconnect', host: 'pair',
    queryParameters: {'v':'1', 'ip': _localIp, 'port':'$port', 'code':_myCode},
  ).toString();

  (String, String)? _decodePairingQr(String data) {
    if (data.length > 256) return null;
    final uri = Uri.tryParse(data);
    if (uri == null || uri.scheme != 'almasconnect' || uri.host != 'pair' ||
        uri.queryParameters['v'] != '1' ||
        uri.queryParameters['port'] != '$port') return null;
    final ip = uri.queryParameters['ip'] ?? '';
    final code = uri.queryParameters['code'] ?? '';
    if (!_isLocalIp(ip) || !RegExp(r'^\d{6}$').hasMatch(code)) return null;
    return (ip, code);
  }

  Future<void> _showQr() async {
    if (!_isLocalIp(_localIp) || !mounted) {
      _log('QR үшін бір Wi-Fi не хотспотқа қосылыңыз');
      return;
    }
    await showDialog<void>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('Қосылу QR-коды'),
        content: SingleChildScrollView(
          child: Column(mainAxisSize: MainAxisSize.min, children: [
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(color: Colors.white,
                borderRadius: BorderRadius.circular(12)),
              child: QrImageView(
                data: _myPairingQr, version: QrVersions.auto,
                size: 240, backgroundColor: Colors.white,
              ),
            ),
            const SizedBox(height: 8),
            SelectableText('$_localIp:$port'),
            SelectableText('6 таңбалы код: $_myCode'),
            const SizedBox(height: 8),
            const Text(
              'Екінші құрылғыдан «QR сканерлеу» таңдаңыз. '
              'Интернет қажет емес, бірақ бір желіде болу керек.',
              textAlign: TextAlign.center,
            ),
          ]),
        ),
        actions: [TextButton(
          onPressed: () => Navigator.pop(dialogContext),
          child: const Text('Жабу'),
        )],
      ),
    );
  }

  Future<void> _scanQr() async {
    if (!Platform.isAndroid || !mounted) return;
    var accepted = false;
    final scanned = await showDialog<String>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('Ноутбук QR-кодын сканерлеу'),
        content: SizedBox(width: 290, height: 300, child: MobileScanner(
          onDetect: (capture) {
            if (accepted) return;
            for (final barcode in capture.barcodes) {
              final raw = barcode.rawValue;
              if (raw == null || _decodePairingQr(raw) == null) continue;
              accepted = true;
              Navigator.pop(dialogContext, raw);
              break;
            }
          },
        )),
        actions: [
          TextButton(onPressed: () => Navigator.pop(dialogContext),
              child: const Text('Бас тарту')),
        ],
      ),
    );
    if (scanned == null || !mounted) return;
    final data = _decodePairingQr(scanned);
    if (data == null) { _log('QR-код дұрыс емес'); return; }
    final ip = data.$1, code = data.$2;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('Қосылуды растау'),
        content: Text('Құрылғы: $ip\nКод: $code\n'
            'Тек сенімді құрылғыға қосылыңыз.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(dialogContext, false),
              child: const Text('Болдырмау')),
          FilledButton(onPressed: () => Navigator.pop(dialogContext, true),
              child: const Text('Қосылу')),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;
    _ipController.text = ip;
    _codeController.text = code;
    await _pair();
  }

  Future<void> _pair() async {
    final host = _ipController.text.trim();
    if (InternetAddress.tryParse(host)?.type != InternetAddressType.IPv4) {
      _log('Ноутбук/телефон IPv4 адресін дұрыс енгізіңіз');
      return;
    }
    if (!RegExp(r'^\d{6}$').hasMatch(_codeController.text.trim())) {
      _log('6 таңбалы кодты енгізіңіз');
      return;
    }
    setState(() => _busy = true);
    final client = HttpClient()..connectionTimeout = const Duration(seconds: 8);
    try {
      final request = await client.postUrl(Uri.parse('http://$host:$port/pair'));
      request.headers.contentType = ContentType.json;
      request.write(jsonEncode({'code': _codeController.text.trim(), 'callbackToken': _myToken, 'device': _deviceName}));
      final response = await request.close();
      final data = jsonDecode(await utf8.decoder.bind(response).join()) as Map<String, dynamic>;
      if (response.statusCode != HttpStatus.ok || data['token'] is! String) {
        throw Exception(data['error'] ?? 'Жұптау қатесі');
      }
      if (!mounted) return;
      setState(() {
        _remoteIp = host;
        _remoteToken = data['token'] as String;
        _remoteName = (data['device'] ?? 'Құрылғы').toString();
      });
      _log('Қосылды: $_remoteName ($host)');
    } catch (e) {
      _log('Жұптау сәтсіз: $e');
    } finally {
      client.close(force: true);
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _sendText() async {
    if (!_connected || _textController.text.trim().isEmpty) return;
    setState(() => _busy = true);
    final client = HttpClient()..connectionTimeout = const Duration(seconds: 8);
    try {
      final request = await client.postUrl(Uri.parse('http://$_remoteIp:$port/text'));
      request.headers.set('x-almas-token', _remoteToken!);
      request.headers.contentType = ContentType.json;
      request.write(jsonEncode({'text': _textController.text}));
      final response = await request.close();
      if (response.statusCode != 200) throw Exception(await utf8.decoder.bind(response).join());
      _log('Мәтін жіберілді');
    } catch (e) {
      _log('Мәтін жіберілмеді: $e');
    } finally {
      client.close(force: true);
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _sendFile(File file, {required String mode, required String kind}) async {
    if (!_connected) return;
    setState(() {
      _busy = true;
      _progress = 0;
    });
    final client = HttpClient()..connectionTimeout = const Duration(seconds: 12);
    try {
      final length = await file.length();
      final name = file.uri.pathSegments.last;
      final uri = Uri.parse('http://$_remoteIp:$port/upload').replace(queryParameters: {
        'name': name, 'mode': mode, 'kind': kind,
      });
      final request = await client.postUrl(uri);
      request.headers.set('x-almas-token', _remoteToken!);
      request.headers.contentType = ContentType.binary;
      request.contentLength = length;
      var sent = 0;
      var lastUpdate = DateTime.now();
      await request.addStream(file.openRead().map((chunk) {
        sent += chunk.length;
        final now = DateTime.now();
        if (now.difference(lastUpdate).inMilliseconds > 150 || sent == length) {
          lastUpdate = now;
          if (mounted) setState(() => _progress = length == 0 ? 1 : sent / length);
        }
        return chunk;
      }));
      final response = await request.close();
      final body = await utf8.decoder.bind(response).join();
      if (response.statusCode != 200) throw Exception(body);
      final info = jsonDecode(body) as Map<String, dynamic>;
      _log('Жіберілді: $name${info['clipboard'] == null ? '' : ' (${info['clipboard']})'}');
    } catch (e) {
      _log('Файл жіберілмеді: $e');
    } finally {
      client.close(force: true);
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<String?> _ask(String title, List<String> choices) => showDialog<String>(
        context: context,
        builder: (ctx) => AlertDialog(
          title: Text(title),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: choices.map((choice) => ListTile(
              title: Text(choice),
              onTap: () => Navigator.pop(ctx, choice),
            )).toList(),
          ),
        ),
      );

  Future<void> _chooseMedia() async {
    if (!_connected || _busy) return;
    final destination = await _ask('Қайда жібереміз?', ['Downloads', 'Буфер']);
    if (destination == null || !mounted) return;
    final kindChoice = await _ask('Не жібересің?', ['Фото', 'Видео']);
    if (kindChoice == null || !mounted) return;
    final source = await _ask('Қайдан аламыз?', ['Галерея', 'Камера']);
    if (source == null || !mounted) return;
    final kind = kindChoice == 'Фото' ? 'photo' : 'video';
    File? file;
    try {
      if (source == 'Камера') {
        if (!Platform.isAndroid) {
          _log('Камерадан түсіру Android-та қолжетімді');
          return;
        }
        final picker = ImagePicker();
        final XFile? picked = kind == 'photo'
            ? await picker.pickImage(source: ImageSource.camera, imageQuality: 95)
            : await picker.pickVideo(source: ImageSource.camera);
        if (picked != null) file = File(picked.path);
      } else {
        final pickedFile = await FilePicker.pickFile(
          type: kind == 'photo' ? FileType.image : FileType.video,
        );
        final path = pickedFile?.path;
        if (path != null) file = File(path);
      }
      if (file != null) await _sendFile(file, mode: destination == 'Буфер' ? 'clipboard' : 'downloads', kind: kind);
    } catch (e) {
      _log('Таңдау қатесі: $e');
    }
  }

  Future<void> _chooseFile() async {
    if (!_connected || _busy) return;
    try {
      final pickedFile = await FilePicker.pickFile(type: FileType.any);
      final filePath = pickedFile?.path;
      if (filePath != null) await _sendFile(File(filePath), mode: 'downloads', kind: 'file');
    } catch (e) {
      _log('Файл таңдау қатесі: $e');
    }
  }

  @override
  void dispose() {
    _server?.close(force: true);
    _updateTimer?.cancel();
    _updater.cancel();
    _ipController.dispose();
    _codeController.dispose();
    _textController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(title: const Text('ALMAS CONNECT  ·  v$almasVersion')),
        body: Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 680),
            child: ListView(
              padding: const EdgeInsets.all(16),
              children: [
                Card(child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
                    const Text('Автожаңарту', style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold)),
                    const SizedBox(height: 6),
                    Text(_updateState),
                    const SizedBox(height: 8),
                    SwitchListTile(
                      title: const Text('Wi-Fi кезінде автоматты жүктеу'),
                      subtitle: const Text('Әдепкіде өшірулі. Хотспот та Wi-Fi деп танылуы мүмкін! Мобильді трафикті тек өзің бақыла.'),
                      value: _autoWifiUpdates,
                      onChanged: _setAutoWifiUpdates,
                    ),
                    if (_downloadingUpdate) ...[
                      LinearProgressIndicator(value: _updateProgress > 0 ? _updateProgress : null),
                      const SizedBox(height: 6),
                      Text('${(_updateProgress * 100).toStringAsFixed(1)}%'),
                      TextButton.icon(
                        onPressed: _updater.cancel,
                        icon: const Icon(Icons.pause),
                        label: const Text('Тоқтату (кейін жалғастыру)'),
                      ),
                    ],
                    Wrap(spacing: 8, children: [
                      OutlinedButton.icon(
                        onPressed: _checkingUpdates || _downloadingUpdate
                            ? null : () => unawaited(_checkUpdates()),
                        icon: const Icon(Icons.refresh),
                        label: const Text('Тексеру'),
                      ),
                      if (_latestUpdate != null && !_downloadingUpdate)
                        FilledButton.icon(
                          onPressed: _downloadedUpdate == null
                              ? _downloadUpdate : _offerInstall,
                          icon: Icon(_downloadedUpdate == null
                              ? Icons.download : Icons.system_update),
                          label: Text(_downloadedUpdate == null
                              ? 'Жүктеу / жалғастыру' : 'Орнату'),
                        ),
                    ]),
                    const Text('Тексеру үшін интернет қажет. Жүктелген бөлімдер сақталады; жаңа файл толық тексерілмей орнатылмайды.', style: TextStyle(fontSize: 12)),
                  ]),
                )),
                const SizedBox(height: 12),
                Card(child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    const Text('Оффлайн желі', style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold)),
                    const SizedBox(height: 8),
                    Text('Күйі: $_status'),
                    SelectableText('Осы құрылғы: $_localIp:$port'),
                    SelectableText('Жұптау коды: $_myCode', style: const TextStyle(fontSize: 24, fontWeight: FontWeight.bold)),
                    const Text('Екі құрылғы бір Wi-Fi желісінде не хотспотта болуы тиіс.'),
                    if (_localIps.length > 1) ...[
                      const SizedBox(height: 8),
                      DropdownButtonFormField<String>(
                        initialValue: _localIp,
                        decoration: const InputDecoration(
                          labelText: 'QR үшін IP таңдау',
                          border: OutlineInputBorder(),
                        ),
                        items: _localIps.map((ip) => DropdownMenuItem(
                          value: ip, child: Text(ip),
                        )).toList(),
                        onChanged: (ip) {
                          if (ip != null) setState(() => _localIp = ip);
                        },
                      ),
                    ],
                    const SizedBox(height: 8),
                    Wrap(spacing: 8, runSpacing: 8, children: [
                      FilledButton.icon(
                        onPressed: _isLocalIp(_localIp) ? _showQr : null,
                        icon: const Icon(Icons.qr_code_2),
                        label: const Text('QR көрсету'),
                      ),
                      if (Platform.isAndroid)
                        OutlinedButton.icon(
                          onPressed: _busy ? null : _scanQr,
                          icon: const Icon(Icons.qr_code_scanner),
                          label: const Text('QR сканерлеу'),
                        ),
                    ]),
                    const SizedBox(height: 12),
                    const SizedBox(height: 12),
                    TextField(controller: _ipController, decoration: const InputDecoration(labelText: 'Екінші құрылғының IP адресі', hintText: '192.168.1.10', border: OutlineInputBorder())),
                    const SizedBox(height: 8),
                    TextField(controller: _codeController, keyboardType: TextInputType.number, decoration: const InputDecoration(labelText: 'Екінші құрылғының 6 таңбалы коды', border: OutlineInputBorder())),
                    const SizedBox(height: 8),
                    FilledButton.icon(onPressed: _busy ? null : _pair, icon: const Icon(Icons.link), label: const Text('Құрылғыларды қосу')),
                    if (_connected) Text('Қосылған: $_remoteName ($_remoteIp)', style: const TextStyle(color: Colors.greenAccent)),
                  ]),
                )),
                const SizedBox(height: 12),
                Card(child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
                    const Text('Файлдар және камера', style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold)),
                    const SizedBox(height: 12),
                    FilledButton.icon(onPressed: _connected && !_busy ? _chooseMedia : null, icon: const Icon(Icons.photo_camera), label: const Text('Ноутбукке фото / видео жіберу')),
                    OutlinedButton.icon(onPressed: _connected && !_busy ? _chooseFile : null, icon: const Icon(Icons.folder_open), label: const Text('Кез келген файлды жіберу')),
                    if (_busy) LinearProgressIndicator(value: _progress > 0 ? _progress : null),
                  ]),
                )),
                const SizedBox(height: 12),
                Card(child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
                    const Text('Мәтін → буфер', style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold)),
                    const SizedBox(height: 8),
                    TextField(controller: _textController, maxLines: 3, decoration: const InputDecoration(hintText: 'Сілтеме немесе мәтін...', border: OutlineInputBorder())),
                    const SizedBox(height: 8),
                    FilledButton.icon(onPressed: _connected && !_busy ? _sendText : null, icon: const Icon(Icons.copy), label: const Text('Екінші құрылғының буферіне жіберу')),
                  ]),
                )),
                const SizedBox(height: 12),
                Card(child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    const Text('Оқиғалар', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
                    ..._events.take(10).map((event) => Padding(padding: const EdgeInsets.only(top: 8), child: Text(event))),
                    if (_events.isEmpty) const Text('Қосылу және жіберу нәтижелері осында шығады.'),
                  ]),
                )),
                const SizedBox(height: 12),
                const Text('v0.2 — тек ортақ LAN / Wi-Fi хотспот. Ашық Wi-Fi желісінде қолданбаңыз: желілік деректер әлі шифрланбаған. Онлайн сервер, экран және Smart Mouse келесі кезеңде.', style: TextStyle(fontSize: 12)),
              ],
            ),
          ),
        ),
      );
}
