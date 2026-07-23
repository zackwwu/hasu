import 'package:flutter/material.dart';
import 'ui/screens/home_screen.dart';

class TileLayoutApp extends StatelessWidget {
  const TileLayoutApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Tile Layout',
      theme: ThemeData(
        colorSchemeSeed: Colors.blue,
        useMaterial3: true,
      ),
      home: const HomeScreen(),
    );
  }
}
