import 'package:flutter/material.dart';

import '../../road_mode/road_mode_placeholder.dart';
import '../../yard_mode/yard_mode_placeholder.dart';

class ModeShell extends StatefulWidget {
  const ModeShell({super.key});

  @override
  State<ModeShell> createState() => _ModeShellState();
}

class _ModeShellState extends State<ModeShell> {
  int _selectedIndex = 0;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('TrailerSaaS')),
      body: SafeArea(
        child: _selectedIndex == 0
            ? const RoadModePlaceholder()
            : const YardModePlaceholder(),
      ),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _selectedIndex,
        onDestinationSelected: (index) {
          setState(() => _selectedIndex = index);
        },
        destinations: const [
          NavigationDestination(
            icon: Icon(Icons.local_shipping_outlined),
            selectedIcon: Icon(Icons.local_shipping),
            label: 'Road Mode',
          ),
          NavigationDestination(
            icon: Icon(Icons.warehouse_outlined),
            selectedIcon: Icon(Icons.warehouse),
            label: 'Yard Mode',
          ),
        ],
      ),
    );
  }
}
