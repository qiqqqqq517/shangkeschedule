import 'package:flutter/cupertino.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';
import 'package:provider/provider.dart';
import 'package:shangkeschedule_flutter/main.dart';
import 'package:shangkeschedule/ui/theme/ui_theme_manager.dart';

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();
  testWidgets('Full app navigation and UI checks', (WidgetTester tester) async {
    await tester.pumpWidget(
      ChangeNotifierProvider(
        create: (_) => UIThemeManager(),
        child: const MyApp(),
      ),
    );
    // Verify TabBar exists
    expect(find.byType(CupertinoTabBar), findsOneWidget);
    // Tap Today tab and check large title
    await tester.tap(find.text('今日'));
    await tester.pumpAndSettle();
    expect(find.text('今日课表'), findsOneWidget);
    // Tap Schedule tab and check grid exists
    await tester.tap(find.text('课表'));
    await tester.pumpAndSettle();
    expect(find.text('课表'), findsOneWidget);
    // Verify a grid cell exists (first child)
    final gridCell = find.descendant(
      of: find.byType(SliverGrid),
      matching: find.byType(Container),
    );
    expect(gridCell, findsWidgets);
    // Tap Settings tab and check theme toggle
    await tester.tap(find.text('我的'));
    await tester.pumpAndSettle();
    expect(find.text('设置'), findsOneWidget);
    // Verify CupertinoSwitch exists for iOS style toggle
    expect(find.byType(CupertinoSwitch), findsOneWidget);
  });
}
