import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:connectivity_plus/connectivity_plus.dart';
import 'package:crypto/crypto.dart';
import 'package:open_filex/open_filex.dart';
import 'package:path_provider/path_provider.dart';

const String almasVersion = '0.2.0';

class AlmasRelease {
  const AlmasRelease({
    required this.version, required this.fileName,
    required this.url, required this.sha256Hex, required this.size,
  });
  final String version;
  final String fileName;
  final Uri url;
  final String sha256Hex;
  final int size;
}

class AlmasUpdater {
  static const String _repo = 'jjjjjjjjjjjjjjae-hub/Chatgpt-Almas-';
  static const int _maxSize = 400 * 1024 * 1024;
  bool _cancelled = false;
  void cancel() => _cancelled = true;

  static List<int> _parts(String value) => value
      .replaceFirst(RegExp(r'^v'), '').split('-').first
      .split('.').map((x) => int.tryParse(x) ?? 0).toList();

  static bool isNewer(String a, String b) {
    final newer = _parts(a), old = _parts(b);
    for (var i = 0; i < 3; i++) {
      final x = i < newer.length ? newer[i] : 0;
      final y = i < old.length ? old[i] : 0;
      if (x != y) return x > y;
    }
    return false;
  }

  Future<AlmasRelease?> check() async {
    if (!Platform.isAndroid && !Platform.isWindows) return null;
    final client = HttpClient()..connectionTimeout = const Duration(seconds: 12);
    try {
      final request = await client.getUrl(
        Uri.https('api.github.com', '/repos/$_repo/releases/latest'),
      );
      request.headers.set(HttpHeaders.userAgentHeader, 'ALMAS-CONNECT/$almasVersion');
      request.headers.set(HttpHeaders.acceptHeader, 'application/vnd.github+json');
      final response = await request.close().timeout(const Duration(seconds: 20));
      if (response.statusCode == 404) {
        await response.drain();
        return null;
      }
      if (response.statusCode != 200) {
        await response.drain();
        throw HttpException('GitHub HTTP ${response.statusCode}');
      }
      final data = jsonDecode(await utf8.decoder.bind(response).join())
          as Map<String, dynamic>;
      final version = (data['tag_name'] ?? '').toString()
          .replaceFirst(RegExp(r'^v'), '');
      if (!RegExp(r'^\d+\.\d+\.\d+$').hasMatch(version) ||
          !isNewer(version, almasVersion)) return null;

      final fileName = Platform.isAndroid
          ? 'ALMAS-CONNECT-Android.apk'
          : 'ALMAS-CONNECT-Windows.zip';
      if (data['assets'] is! List) throw const FormatException('Релиз файлдары жоқ');
      for (final item in data['assets'] as List) {
        if (item is! Map || item['name'] != fileName) continue;
        final url = Uri.tryParse((item['browser_download_url'] ?? '').toString());
        final digest = (item['digest'] ?? '').toString();
        final size = item['size'];
        if (url == null || url.scheme != 'https' || url.host != 'github.com' ||
            !url.path.startsWith('/$_repo/releases/download/') ||
            !RegExp(r'^sha256:[a-fA-F0-9]{64}$').hasMatch(digest) ||
            size is! int || size <= 0 || size > _maxSize) {
          throw const FormatException('Жаңарту файлының қауіпсіздік деректері жарамсыз');
        }
        return AlmasRelease(
          version: version, fileName: fileName,
          url: url, sha256Hex: digest.substring(7).toLowerCase(), size: size,
        );
      }
      throw const FormatException('APK немесе Windows ZIP табылмады');
    } finally {
      client.close(force: true);
    }
  }

  /// Opt-in only. On Windows, Wi-Fi can still be a metered phone hotspot.
  Future<bool> canAutoDownload() async {
    try {
      final results = await Connectivity().checkConnectivity();
      return !results.contains(ConnectivityResult.mobile) &&
          (results.contains(ConnectivityResult.wifi) ||
           results.contains(ConnectivityResult.ethernet));
    } catch (_) {
      return false;
    }
  }

  Future<File> download(
    AlmasRelease release, {
    required void Function(int received, int total) onProgress,
  }) async {
    _cancelled = false;
    final dir = Directory(
      '${(await getApplicationSupportDirectory()).path}${Platform.pathSeparator}updates',
    );
    await dir.create(recursive: true);
    final part = File('${dir.path}${Platform.pathSeparator}${release.version}-${release.fileName}.part');
    final ready = File('${dir.path}${Platform.pathSeparator}${release.version}-${release.fileName}');

    Future<bool> verify(File file) async {
      if (!await file.exists() || await file.length() != release.size) return false;
      final digest = await sha256.bind(file.openRead()).first;
      return digest.toString().toLowerCase() == release.sha256Hex;
    }

    if (await verify(ready)) {
      onProgress(release.size, release.size);
      return ready;
    }
    if (await ready.exists()) await ready.delete();

    for (var attempt = 0; attempt < 3; attempt++) {
      if (_cancelled) throw const FileSystemException('Жүктеу тоқтатылды');
      final client = HttpClient()..connectionTimeout = const Duration(seconds: 20);
      try {
        var offset = await part.exists() ? await part.length() : 0;
        if (offset > release.size) {
          await part.delete();
          offset = 0;
        }
        if (offset == release.size) {
          if (await verify(part)) {
            await part.rename(ready.path);
            onProgress(release.size, release.size);
            return ready;
          }
          await part.delete(); // Bad completed partial: retry from byte zero.
          offset = 0;
        }
        final request = await client.getUrl(release.url);
        request.headers.set(HttpHeaders.userAgentHeader, 'ALMAS-CONNECT/$almasVersion');
        if (offset > 0) request.headers.set('Range', 'bytes=$offset-');
        final response = await request.close().timeout(const Duration(seconds: 30));
        final partial = response.statusCode == HttpStatus.partialContent;
        if (response.statusCode != HttpStatus.ok && !partial) {
          await response.drain();
          throw HttpException('Жүктеу HTTP ${response.statusCode}');
        }
        if (partial && offset > 0) {
          final range = response.headers.value('content-range') ?? '';
          if (!range.startsWith('bytes $offset-')) {
            await response.drain();
            throw const FormatException('Жүктеу бөлігі дұрыс емес');
          }
        }
        // If the server ignored the Range header, restart from zero.
        if (!partial || offset == 0) offset = 0;
        final sink = part.openWrite(mode: offset > 0 ? FileMode.append : FileMode.write);
        var received = offset;
        try {
          await for (final bytes in response) {
            if (_cancelled) throw const FileSystemException('Жүктеу тоқтатылды');
            received += bytes.length;
            if (received > release.size) throw const FormatException('Файл тым үлкен');
            sink.add(bytes);
            onProgress(received, release.size);
          }
          await sink.flush();
        } finally {
          await sink.close();
        }
        if (received != release.size) throw const FormatException('Жүктеу толық емес');
        if (!await verify(part)) {
          await part.delete();
          throw const FormatException('SHA-256 сәйкес емес: жаңарту қабылданбады');
        }
        await part.rename(ready.path);
        onProgress(release.size, release.size);
        return ready;
      } on FileSystemException {
        rethrow;
      } on FormatException {
        rethrow;
      } catch (_) {
        if (attempt == 2) rethrow;
        await Future<void>.delayed(Duration(seconds: 2 * (attempt + 1)));
      } finally {
        client.close(force: true);
      }
    }
    throw const FileSystemException('Жаңарту жүктелмеді');
  }

  /// Android requires user confirmation in the platform APK installer.
  Future<void> installAndroid(File apk) async {
    final result = await OpenFilex.open(
      apk.path, type: 'application/vnd.android.package-archive',
    );
    if (result.type != ResultType.done) throw Exception(result.message);
  }

  /// On Windows a separate PowerShell process replaces files *after exit*.
  /// Call only in response to explicit confirmation from the user.
  Future<void> installWindows(File zip) async {
    if (!Platform.isWindows) throw UnsupportedError('Windows ғана');
    final exe = File(Platform.resolvedExecutable).absolute;
    final directory = await getApplicationSupportDirectory();
    final script = File('${directory.path}${Platform.pathSeparator}apply-almas-update.ps1');

    String quote(String input) => "'${input.replaceAll("'", "''")}'";
    final commands = '''
\$ErrorActionPreference = 'Stop'
\$oldProcess = $pid
\$zipPath = ${quote(zip.path)}
\$target = ${quote(exe.parent.path)}
\$exePath = ${quote(exe.path)}
\$staging = Join-Path \$env:TEMP ('ALMAS-UPDATE-' + [guid]::NewGuid())
\$log = Join-Path \$env:TEMP 'ALMAS-CONNECT-update.log'
try {
  while (Get-Process -Id \$oldProcess -ErrorAction SilentlyContinue) {
    Start-Sleep -Milliseconds 350
  }
  New-Item -ItemType Directory -Path \$staging -Force | Out-Null
  Expand-Archive -LiteralPath \$zipPath -DestinationPath \$staging -Force
  if (-not (Test-Path -LiteralPath (Join-Path \$staging 'almas_connect.exe'))) {
    throw 'No almas_connect.exe in update ZIP'
  }
  foreach (\$entry in (Get-ChildItem -LiteralPath \$staging -Force)) {
    Copy-Item -LiteralPath \$entry.FullName -Destination \$target -Force -Recurse
  }
  Start-Process -FilePath \$exePath
} catch {
  \$_ | Out-String | Set-Content -LiteralPath \$log -Encoding UTF8
} finally {
  Remove-Item -LiteralPath \$staging -Recurse -Force -ErrorAction SilentlyContinue
}
''';
    await script.writeAsString(commands);
    await Process.start('powershell.exe', [
      '-NoProfile', '-ExecutionPolicy', 'Bypass', '-WindowStyle', 'Hidden',
      '-File', script.path,
    ], mode: ProcessStartMode.detached);
    exit(0);
  }
}
