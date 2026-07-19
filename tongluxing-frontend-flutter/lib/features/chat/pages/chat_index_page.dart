import 'package:flutter/material.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';
import 'package:provider/provider.dart';

import '../../../app/app_session.dart';
import '../../../app/theme.dart';
import '../../../common/widgets/app_widgets.dart';
import '../../../data/models/app_models.dart';
import '../../../data/services/app_services.dart';
import 'chat_session_page.dart';
import 'chat_management_pages.dart';

class ChatIndexPage extends StatefulWidget {
  const ChatIndexPage({super.key});
  @override
  State<ChatIndexPage> createState() => _ChatIndexPageState();
}

class _ChatIndexPageState extends State<ChatIndexPage> {
  bool loading = true;
  String? error;
  List<ConversationModel> rows = [];

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => load());
  }

  Future<void> load() async {
    setState(() => loading = true);
    try {
      rows = await ChatService(context.read<AppSession>().api).conversations();
      error = null;
    } catch (e) {
      error = e.toString();
    }
    if (mounted) setState(() => loading = false);
  }

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: Column(
        children: [
          PageHeading(
            '消息',
            action: IconButton.filledTonal(
              onPressed: () => Navigator.push(
                context,
                MaterialPageRoute(builder: (_) => const JoinApplicationsPage()),
              ),
              icon: const Icon(LucideIcons.userPlus),
            ),
          ),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 22),
            child: TextField(
              decoration: InputDecoration(
                hintText: '搜索聊天记录',
                prefixIcon: const Icon(LucideIcons.search),
                border: OutlineInputBorder(
                  borderSide: BorderSide.none,
                  borderRadius: BorderRadius.circular(22),
                ),
              ),
            ),
          ),
          Expanded(
            child: AsyncPanel(
              loading: loading,
              error: error,
              onRetry: load,
              child: RefreshIndicator(
                onRefresh: load,
                child: rows.isEmpty
                    ? ListView(
                        padding: const EdgeInsets.only(top: 120),
                        children: const [
                          Icon(
                            LucideIcons.messageCircle,
                            size: 54,
                            color: AppColors.muted,
                          ),
                          SizedBox(height: 14),
                          Text(
                            '暂无进行中的行程群聊',
                            textAlign: TextAlign.center,
                            style: TextStyle(fontWeight: FontWeight.w700),
                          ),
                          SizedBox(height: 6),
                          Text(
                            '队长开启行程后，系统会自动创建群聊',
                            textAlign: TextAlign.center,
                            style: TextStyle(color: AppColors.muted),
                          ),
                        ],
                      )
                    : ListView.separated(
                        keyboardDismissBehavior:
                            ScrollViewKeyboardDismissBehavior.onDrag,
                        padding: const EdgeInsets.fromLTRB(22, 12, 22, 24),
                        itemCount: rows.length,
                        separatorBuilder: (_, _) => const Divider(height: 1),
                        itemBuilder: (context, index) {
                          final row = rows[index];
                          final time = row.time.length > 10
                              ? row.time.substring(0, 10)
                              : row.time;
                          return ListTile(
                            contentPadding: const EdgeInsets.symmetric(
                              vertical: 8,
                            ),
                            leading: CircleAvatar(
                              radius: 28,
                              backgroundColor: AppColors.primarySoft,
                              child: Text(
                                row.name.isEmpty
                                    ? '聊'
                                    : row.name.characters.first,
                                style: const TextStyle(
                                  color: AppColors.primary,
                                  fontWeight: FontWeight.w700,
                                ),
                              ),
                            ),
                            title: Text(
                              row.name,
                              style: const TextStyle(
                                fontWeight: FontWeight.w700,
                              ),
                            ),
                            subtitle: Padding(
                              padding: const EdgeInsets.only(top: 6),
                              child: Text(
                                row.preview,
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                              ),
                            ),
                            trailing: Text(
                              time,
                              style: const TextStyle(
                                fontSize: 11,
                                color: AppColors.muted,
                              ),
                            ),
                            onTap: () => Navigator.push(
                              context,
                              MaterialPageRoute(
                                builder: (_) =>
                                    ChatSessionPage(conversation: row),
                              ),
                            ),
                          );
                        },
                      ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}
