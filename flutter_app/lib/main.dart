import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const ModemApp());
}

class ModemApp extends StatelessWidget {
  const ModemApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'dRecharge Modem v1',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(
          seedColor: const Color(0xFF0E5A8A),
          secondary: const Color(0xFFDA7B27),
        ),
        scaffoldBackgroundColor: const Color(0xFFF4F7FB),
        useMaterial3: true,
      ),
      home: const DashboardPage(),
    );
  }
}

class DashboardPage extends StatefulWidget {
  const DashboardPage({super.key});

  @override
  State<DashboardPage> createState() => _DashboardPageState();
}

class _DashboardPageState extends State<DashboardPage> {
  final NativeBridge _bridge = const NativeBridge();
  DashboardState? _state;
  StreamSubscription<Map<String, dynamic>>? _eventSubscription;
  bool _loading = true;
  final List<String> _eventLogs = <String>[];

  @override
  void initState() {
    super.initState();
    _loadState();
    _eventSubscription = _bridge.events.listen((event) {
      final message = '${event['type'] ?? 'event'}: ${event['message'] ?? ''}';
      if (!mounted) {
        return;
      }
      setState(() {
        _eventLogs.insert(0, message);
      });
    });
  }

  @override
  void dispose() {
    _eventSubscription?.cancel();
    super.dispose();
  }

  Future<void> _loadState() async {
    setState(() {
      _loading = true;
    });
    try {
      final state = await _bridge.loadState();
      if (!mounted) {
        return;
      }
      setState(() {
        _state = state;
        _loading = false;
      });
    } catch (error) {
      if (!mounted) {
        return;
      }
      setState(() {
        _loading = false;
      });
      _showMessage('Failed to load app state: $error');
    }
  }

  void _showMessage(String message) {
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(message)));
  }

  Future<void> _requestPermissions() async {
    final response = await _bridge.requestPermissions();
    final granted = response['granted'] == true;
    _showMessage(granted ? 'Permissions granted' : 'Some permissions are still missing');
    await _loadState();
  }

  Future<void> _openAccessibility() async {
    await _bridge.openAccessibilitySettings();
    _showMessage('Accessibility settings opened');
  }

  Future<void> _editDomain() async {
    final current = _state?.settings.domain ?? '';
    final controller = TextEditingController(text: current);
    final value = await showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Change API Link'),
        content: TextField(
          controller: controller,
          autofocus: true,
          decoration: const InputDecoration(hintText: 'Domain'),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(context).pop(controller.text.trim()),
            child: const Text('Save'),
          ),
        ],
      ),
    );

    if (value == null || value.isEmpty) {
      return;
    }

    await _bridge.saveDomain(value);
    await _loadState();
    _showMessage('Domain saved');
  }

  Future<void> _editInterval() async {
    final current = _state?.settings.interval ?? '';
    final controller = TextEditingController(text: current);
    final value = await showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Thread Time Interval'),
        content: TextField(
          controller: controller,
          keyboardType: TextInputType.number,
          autofocus: true,
          decoration: const InputDecoration(hintText: 'Seconds'),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(context).pop(controller.text.trim()),
            child: const Text('Save'),
          ),
        ],
      ),
    );

    if (value == null || value.isEmpty) {
      return;
    }

    await _bridge.saveInterval(value);
    await _loadState();
    _showMessage('Interval saved');
  }

  Future<void> _editSim(String slotKey, SimSettings sim) async {
    final numberController = TextEditingController(text: sim.number);
    final pinController = TextEditingController(text: sim.pin);
    final minBalanceController = TextEditingController(text: sim.minBalance);
    final timeController = TextEditingController(text: sim.time);

    final saved = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(slotKey == 'sim1' ? 'SIM 1 Settings' : 'SIM 2 Settings'),
        content: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextField(
                controller: numberController,
                keyboardType: TextInputType.phone,
                decoration: const InputDecoration(labelText: 'SIM Number'),
              ),
              const SizedBox(height: 12),
              TextField(
                controller: pinController,
                keyboardType: TextInputType.number,
                obscureText: true,
                decoration: const InputDecoration(labelText: 'PIN'),
              ),
              const SizedBox(height: 12),
              TextField(
                controller: minBalanceController,
                keyboardType: TextInputType.number,
                decoration: const InputDecoration(labelText: 'Min Balance'),
              ),
              const SizedBox(height: 12),
              TextField(
                controller: timeController,
                keyboardType: TextInputType.number,
                decoration: const InputDecoration(labelText: 'Time Interval'),
              ),
            ],
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(context).pop(true),
            child: const Text('Save'),
          ),
        ],
      ),
    );

    if (saved != true) {
      return;
    }

    await _bridge.saveSimSettings(
      slotKey: slotKey,
      number: numberController.text.trim(),
      pin: pinController.text.trim(),
      minBalance: minBalanceController.text.trim(),
      time: timeController.text.trim(),
    );
    await _loadState();
    _showMessage('${slotKey == 'sim1' ? 'SIM 1' : 'SIM 2'} settings saved');
  }

  Future<void> _updateService(String slotKey, int index, ServiceCatalogItem? item) async {
    if (item == null) {
      return;
    }
    await _bridge.saveSimService(
      slotKey: slotKey,
      serviceIndex: index.toString(),
      serviceName: item.name,
      serviceCode: item.code,
    );
    await _loadState();
  }

  Widget _buildStatusStrip(DashboardState state) {
    return Container(
      margin: const EdgeInsets.fromLTRB(16, 16, 16, 8),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        gradient: const LinearGradient(
          colors: [Color(0xFF0E5A8A), Color(0xFF1C7CAD)],
        ),
        borderRadius: BorderRadius.circular(20),
      ),
      child: Wrap(
        runSpacing: 10,
        spacing: 10,
        children: [
          _StatusChip(
            label: state.permissionsGranted ? 'Permissions Ready' : 'Permissions Missing',
            color: state.permissionsGranted ? const Color(0xFF1F8B4C) : const Color(0xFFB54708),
          ),
          _StatusChip(
            label: state.accessibilityEnabled ? 'Accessibility On' : 'Accessibility Off',
            color: state.accessibilityEnabled ? const Color(0xFF1F8B4C) : const Color(0xFFB42318),
          ),
          _StatusChip(
            label: state.settings.isDomainValid ? 'Domain Saved' : 'Domain Missing',
            color: state.settings.isDomainValid ? const Color(0xFF1F8B4C) : const Color(0xFFB54708),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final state = _state;

    return Scaffold(
      appBar: AppBar(
        title: const Text('dRecharge Modem v1'),
        actions: [
          PopupMenuButton<String>(
            onSelected: (value) async {
              switch (value) {
                case 'permissions':
                  await _requestPermissions();
                  break;
                case 'accessibility':
                  await _openAccessibility();
                  break;
                case 'domain':
                  await _editDomain();
                  break;
                case 'interval':
                  await _editInterval();
                  break;
                case 'refresh':
                  await _loadState();
                  break;
                case 'exit':
                  await _bridge.closeApp();
                  break;
              }
            },
            itemBuilder: (context) => const [
              PopupMenuItem(value: 'permissions', child: Text('Request Permissions')),
              PopupMenuItem(value: 'accessibility', child: Text('Open Accessibility')),
              PopupMenuItem(value: 'domain', child: Text('Change API Link')),
              PopupMenuItem(value: 'interval', child: Text('Thread Time Interval')),
              PopupMenuItem(value: 'refresh', child: Text('Refresh')),
              PopupMenuItem(value: 'exit', child: Text('Exit')),
            ],
          ),
        ],
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : state == null
              ? const Center(child: Text('No state available'))
              : RefreshIndicator(
                  onRefresh: _loadState,
                  child: ListView(
                    padding: const EdgeInsets.only(bottom: 24),
                    children: [
                      _buildStatusStrip(state),
                      _SimPanel(
                        title: 'SIM 1',
                        liveInfo: state.simCards.isNotEmpty ? state.simCards[0] : null,
                        settings: state.settings.sim1,
                        serviceCatalog: state.serviceCatalog,
                        eventLogs: _eventLogs,
                        onEditSettings: () => _editSim('sim1', state.settings.sim1),
                        onServiceChanged: (index, item) => _updateService('sim1', index, item),
                      ),
                      _SimPanel(
                        title: 'SIM 2',
                        liveInfo: state.simCards.length > 1 ? state.simCards[1] : null,
                        settings: state.settings.sim2,
                        serviceCatalog: state.serviceCatalog,
                        eventLogs: _eventLogs,
                        onEditSettings: () => _editSim('sim2', state.settings.sim2),
                        onServiceChanged: (index, item) => _updateService('sim2', index, item),
                      ),
                      Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                        child: Card(
                          child: Padding(
                            padding: const EdgeInsets.all(16),
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: const [
                                Text(
                                  'Migration Note',
                                  style: TextStyle(fontSize: 16, fontWeight: FontWeight.w700),
                                ),
                                SizedBox(height: 8),
                                Text(
                                  'The Flutter UI, Android session bridge, SIM discovery, permission flow, and accessibility wiring are active. The full native request-processing engine is the next parity step.',
                                ),
                              ],
                            ),
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
    );
  }
}

class _SimPanel extends StatelessWidget {
  const _SimPanel({
    required this.title,
    required this.liveInfo,
    required this.settings,
    required this.serviceCatalog,
    required this.eventLogs,
    required this.onEditSettings,
    required this.onServiceChanged,
  });

  final String title;
  final SimCardInfo? liveInfo;
  final SimSettings settings;
  final List<ServiceCatalogItem> serviceCatalog;
  final List<String> eventLogs;
  final VoidCallback onEditSettings;
  final void Function(int index, ServiceCatalogItem? item) onServiceChanged;

  @override
  Widget build(BuildContext context) {
    final selectedIndex = int.tryParse(settings.serviceIndex) ?? 0;
    final safeIndex = selectedIndex >= 0 && selectedIndex < serviceCatalog.length ? selectedIndex : 0;

    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 8),
      child: Card(
        elevation: 0,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(22)),
        child: Padding(
          padding: const EdgeInsets.all(18),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          title,
                          style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w700),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          '${liveInfo?.carrierName ?? 'Unknown'} | ${settings.number.isNotEmpty ? settings.number : liveInfo?.number ?? 'No number'} | Id: ${settings.id.isNotEmpty ? settings.id : liveInfo?.slotIndex ?? '-'}',
                          style: const TextStyle(color: Color(0xFF475467)),
                        ),
                      ],
                    ),
                  ),
                  IconButton(
                    onPressed: onEditSettings,
                    icon: const Icon(Icons.settings_applications_outlined),
                  ),
                ],
              ),
              const SizedBox(height: 14),
              Wrap(
                spacing: 12,
                runSpacing: 12,
                children: [
                  _InfoPill(label: 'PIN', value: settings.pin.isEmpty ? '-' : settings.pin),
                  _InfoPill(label: 'Interval', value: settings.time.isEmpty ? '-' : '${settings.time}s'),
                  _InfoPill(label: 'Min Balance', value: settings.minBalance.isEmpty ? '-' : settings.minBalance),
                ],
              ),
              const SizedBox(height: 16),
              DropdownButtonFormField<int>(
                value: safeIndex,
                decoration: const InputDecoration(
                  labelText: 'Service',
                  border: OutlineInputBorder(),
                ),
                items: [
                  for (var i = 0; i < serviceCatalog.length; i++)
                    DropdownMenuItem<int>(
                      value: i,
                      child: Text(serviceCatalog[i].name),
                    ),
                ],
                onChanged: (value) {
                  if (value == null) {
                    return;
                  }
                  onServiceChanged(value, serviceCatalog[value]);
                },
              ),
              const SizedBox(height: 16),
              Row(
                children: [
                  Switch(
                    value: false,
                    onChanged: null,
                  ),
                  const SizedBox(width: 8),
                  const Expanded(
                    child: Text(
                      'Status switch UI is in Flutter. Native polling and queue execution will be connected in the next parity pass.',
                      style: TextStyle(color: Color(0xFF667085)),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 16),
              Container(
                width: double.infinity,
                constraints: const BoxConstraints(minHeight: 120),
                padding: const EdgeInsets.all(14),
                decoration: BoxDecoration(
                  color: const Color(0xFF0B1220),
                  borderRadius: BorderRadius.circular(18),
                ),
                child: eventLogs.isEmpty
                    ? const Text(
                        'No native events yet',
                        style: TextStyle(color: Colors.white70),
                      )
                    : Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          for (final line in eventLogs.take(8))
                            Padding(
                              padding: const EdgeInsets.only(bottom: 6),
                              child: Text(
                                line,
                                style: const TextStyle(
                                  color: Colors.white,
                                  fontFamily: 'monospace',
                                ),
                              ),
                            ),
                        ],
                      ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _InfoPill extends StatelessWidget {
  const _InfoPill({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      decoration: BoxDecoration(
        color: const Color(0xFFEAF2F8),
        borderRadius: BorderRadius.circular(16),
      ),
      child: RichText(
        text: TextSpan(
          style: const TextStyle(color: Color(0xFF0F172A)),
          children: [
            TextSpan(text: '$label: ', style: const TextStyle(fontWeight: FontWeight.w700)),
            TextSpan(text: value),
          ],
        ),
      ),
    );
  }
}

class _StatusChip extends StatelessWidget {
  const _StatusChip({required this.label, required this.color});

  final String label;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.18),
        borderRadius: BorderRadius.circular(999),
        border: Border.all(color: color.withValues(alpha: 0.45)),
      ),
      child: Text(
        label,
        style: TextStyle(color: Colors.white.withValues(alpha: 0.95)),
      ),
    );
  }
}

class NativeBridge {
  const NativeBridge();

  static const MethodChannel _methodChannel = MethodChannel('modem/native');
  static const EventChannel _eventChannel = EventChannel('modem/events');

  Stream<Map<String, dynamic>> get events => _eventChannel.receiveBroadcastStream().map((event) {
        return Map<String, dynamic>.from(event as Map);
      });

  Future<DashboardState> loadState() async {
    final raw = await _methodChannel.invokeMethod<dynamic>('getInitialState');
    return DashboardState.fromMap(Map<String, dynamic>.from(raw as Map));
  }

  Future<Map<String, dynamic>> requestPermissions() async {
    final raw = await _methodChannel.invokeMethod<dynamic>('requestPermissions');
    return Map<String, dynamic>.from(raw as Map);
  }

  Future<void> openAccessibilitySettings() async {
    await _methodChannel.invokeMethod('openAccessibilitySettings');
  }

  Future<void> saveDomain(String domain) async {
    await _methodChannel.invokeMethod('saveDomain', {'domain': domain});
  }

  Future<void> saveInterval(String interval) async {
    await _methodChannel.invokeMethod('saveInterval', {'interval': interval});
  }

  Future<void> saveSimSettings({
    required String slotKey,
    required String number,
    required String pin,
    required String minBalance,
    required String time,
  }) async {
    await _methodChannel.invokeMethod('saveSimSettings', {
      'slotKey': slotKey,
      'number': number,
      'pin': pin,
      'minBalance': minBalance,
      'time': time,
    });
  }

  Future<void> saveSimService({
    required String slotKey,
    required String serviceIndex,
    required String serviceName,
    required String serviceCode,
  }) async {
    await _methodChannel.invokeMethod('saveSimService', {
      'slotKey': slotKey,
      'serviceIndex': serviceIndex,
      'serviceName': serviceName,
      'serviceCode': serviceCode,
    });
  }

  Future<void> closeApp() async {
    await _methodChannel.invokeMethod('closeApp');
  }
}

class DashboardState {
  DashboardState({
    required this.permissionsGranted,
    required this.accessibilityEnabled,
    required this.serviceCatalog,
    required this.simCards,
    required this.settings,
  });

  factory DashboardState.fromMap(Map<String, dynamic> map) {
    final settingsMap = Map<String, dynamic>.from(map['settings'] as Map);
    return DashboardState(
      permissionsGranted: map['permissionsGranted'] == true,
      accessibilityEnabled: map['accessibilityEnabled'] == true,
      serviceCatalog: ((map['serviceCatalog'] as List?) ?? const [])
          .map((item) => ServiceCatalogItem.fromMap(Map<String, dynamic>.from(item as Map)))
          .toList(),
      simCards: ((map['simCards'] as List?) ?? const [])
          .map((item) => SimCardInfo.fromMap(Map<String, dynamic>.from(item as Map)))
          .toList(),
      settings: AppSettings.fromMap(settingsMap),
    );
  }

  final bool permissionsGranted;
  final bool accessibilityEnabled;
  final List<ServiceCatalogItem> serviceCatalog;
  final List<SimCardInfo> simCards;
  final AppSettings settings;
}

class AppSettings {
  AppSettings({
    required this.domain,
    required this.isDomainValid,
    required this.interval,
    required this.sim1,
    required this.sim2,
  });

  factory AppSettings.fromMap(Map<String, dynamic> map) {
    return AppSettings(
      domain: map['domain'] as String? ?? '',
      isDomainValid: map['isDomainValid'] == true,
      interval: map['interval'] as String? ?? '',
      sim1: SimSettings.fromMap(Map<String, dynamic>.from(map['sim1'] as Map)),
      sim2: SimSettings.fromMap(Map<String, dynamic>.from(map['sim2'] as Map)),
    );
  }

  final String domain;
  final bool isDomainValid;
  final String interval;
  final SimSettings sim1;
  final SimSettings sim2;
}

class SimSettings {
  SimSettings({
    required this.valid,
    required this.id,
    required this.number,
    required this.pin,
    required this.minBalance,
    required this.time,
    required this.serviceIndex,
    required this.serviceName,
    required this.serviceCode,
  });

  factory SimSettings.fromMap(Map<String, dynamic> map) {
    return SimSettings(
      valid: map['valid'] == true,
      id: map['id'] as String? ?? '',
      number: map['number'] as String? ?? '',
      pin: map['pin'] as String? ?? '',
      minBalance: map['minBalance'] as String? ?? '',
      time: map['time'] as String? ?? '',
      serviceIndex: map['serviceIndex'] as String? ?? '',
      serviceName: map['serviceName'] as String? ?? '',
      serviceCode: map['serviceCode'] as String? ?? '',
    );
  }

  final bool valid;
  final String id;
  final String number;
  final String pin;
  final String minBalance;
  final String time;
  final String serviceIndex;
  final String serviceName;
  final String serviceCode;
}

class SimCardInfo {
  SimCardInfo({
    required this.slotIndex,
    required this.carrierName,
    required this.number,
  });

  factory SimCardInfo.fromMap(Map<String, dynamic> map) {
    return SimCardInfo(
      slotIndex: map['slotIndex'] as int? ?? 0,
      carrierName: map['carrierName'] as String? ?? '',
      number: map['number'] as String? ?? '',
    );
  }

  final int slotIndex;
  final String carrierName;
  final String number;
}

class ServiceCatalogItem {
  ServiceCatalogItem({
    required this.name,
    required this.code,
  });

  factory ServiceCatalogItem.fromMap(Map<String, dynamic> map) {
    return ServiceCatalogItem(
      name: map['name'] as String? ?? '',
      code: map['code'] as String? ?? '',
    );
  }

  final String name;
  final String code;
}
