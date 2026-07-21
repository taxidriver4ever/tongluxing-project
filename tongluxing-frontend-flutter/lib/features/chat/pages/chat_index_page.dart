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

  Future<void> togglePin(ConversationModel row) async {
    await ChatService(
      context.read<AppSession>().api,
    ).updateSettings(row.id, muted: row.muted, pinned: !row.pinned);
    await load();
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
            padding: const EdgeInsets.symmetric(horizontal: 30),
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
                            '暂时没有行程群聊',
                            textAlign: TextAlign.center,
                            style: TextStyle(fontWeight: FontWeight.w700),
                          ),
                          SizedBox(height: 6),
                          Text(
                            '发布行程或加入车队后，可在这里与同行成员沟通',
                            textAlign: TextAlign.center,
                            style: TextStyle(color: AppColors.muted),
                          ),
                        ],
                      )
                    : ListView.separated(
                        keyboardDismissBehavior:
                            ScrollViewKeyboardDismissBehavior.onDrag,
                        padding: const EdgeInsets.fromLTRB(30, 12, 30, 24),
                        itemCount: rows.length,
                        separatorBuilder: (_, _) => const SizedBox(height: 10),
                        itemBuilder: (context, index) {
                          final row = rows[index];
                          final time = row.time.length > 10
                              ? row.time.substring(0, 10)
                              : row.time;
                          return _SwipeConversationTile(
                            key: ValueKey('conversation-${row.id}'),
                            row: row,
                            time: time,
                            onPin: () => togglePin(row),
                            onOpen: () async {
                              await Navigator.push(
                                context,
                                MaterialPageRoute(
                                  builder: (_) =>
                                      ChatSessionPage(conversation: row),
                                ),
                              );
                              await load();
                            },
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

class _SwipeConversationTile extends StatefulWidget {
  const _SwipeConversationTile({
    required this.row,
    required this.time,
    required this.onPin,
    required this.onOpen,
    super.key,
  });
  final ConversationModel row;
  final String time;
  final Future<void> Function() onPin;
  final Future<void> Function() onOpen;

  @override
  State<_SwipeConversationTile> createState() => _SwipeConversationTileState();
}

class _SwipeConversationTileState extends State<_SwipeConversationTile> {
  static const actionWidth = 94.0;
  double offset = 0;

  void settle() => setState(() => offset = offset < -40 ? -actionWidth : 0);

  @override
  Widget build(BuildContext context) => SizedBox(
    height: 84,
    child: ClipRRect(
      borderRadius: BorderRadius.circular(16),
      child: Stack(
        fit: StackFit.expand,
        children: [
          Align(
            alignment: Alignment.centerRight,
            child: SizedBox(
              width: actionWidth,
              height: double.infinity,
              child: Material(
                color: widget.row.pinned
                    ? const Color(0xFF87909F)
                    : AppColors.primary,
                child: InkWell(
                  onTap: () async {
                    await widget.onPin();
                    if (mounted) setState(() => offset = 0);
                  },
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Icon(
                        widget.row.pinned
                            ? LucideIcons.pinOff
                            : LucideIcons.pin,
                        color: Colors.white,
                      ),
                      const SizedBox(height: 5),
                      Text(
                        widget.row.pinned ? '取消置顶' : '置顶',
                        style: const TextStyle(
                          color: Colors.white,
                          fontWeight: FontWeight.w800,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),
          AnimatedContainer(
            duration: const Duration(milliseconds: 180),
            curve: Curves.easeOutCubic,
            transform: Matrix4.translationValues(offset, 0, 0),
            child: GestureDetector(
              behavior: HitTestBehavior.opaque,
              onHorizontalDragUpdate: (details) {
                setState(
                  () => offset = (offset + details.delta.dx).clamp(
                    -actionWidth,
                    0,
                  ),
                );
              },
              onHorizontalDragEnd: (_) => settle(),
              child: Material(
                color: widget.row.pinned
                    ? const Color(0xFFF0F2F5)
                    : Colors.white,
                child: ListTile(
                  minVerticalPadding: 0,
                  contentPadding: const EdgeInsets.symmetric(horizontal: 12),
                  leading: CircleAvatar(
                    radius: 23,
                    backgroundColor: AppColors.primarySoft,
                    child: Text(
                      widget.row.name.isEmpty
                          ? '聊'
                          : widget.row.name.characters.first,
                      style: const TextStyle(
                        color: AppColors.primary,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                  ),
                  title: Row(
                    children: [
                      Expanded(
                        child: Text(
                          widget.row.name,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(fontWeight: FontWeight.w700),
                        ),
                      ),
                      if (widget.row.pinned)
                        const Padding(
                          padding: EdgeInsets.only(left: 6),
                          child: Icon(
                            LucideIcons.pin,
                            size: 14,
                            color: AppColors.muted,
                          ),
                        ),
                    ],
                  ),
                  subtitle: Padding(
                    padding: const EdgeInsets.only(top: 4),
                    child: Text(
                      widget.row.preview,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
                  trailing: Text(
                    widget.time,
                    style: const TextStyle(
                      fontSize: 11,
                      color: AppColors.muted,
                    ),
                  ),
                  onTap: () {
                    if (offset < 0) {
                      setState(() => offset = 0);
                    } else {
                      widget.onOpen();
                    }
                  },
                ),
              ),
            ),
          ),
        ],
      ),
    ),
  );
}
