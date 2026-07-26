import 'dart:async';

import 'package:flutter/material.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';
import 'package:provider/provider.dart';

import '../../../app/app_session.dart';
import '../../../app/theme.dart';
import '../../../data/models/app_models.dart';
import '../../../data/services/app_services.dart';
import '../../../data/services/follow_service.dart';
import '../../profile/widgets/user_avatar.dart';
import 'chat_session_page.dart';
import 'interaction_messages_page.dart';

class ChatIndexPage extends StatefulWidget {
  const ChatIndexPage({super.key});

  @override
  State<ChatIndexPage> createState() => _ChatIndexPageState();
}

class _ChatIndexPageState extends State<ChatIndexPage> {
  final search = TextEditingController();
  Timer? debounce;
  bool loading = true;
  String? error;
  List<ConversationModel> allRows = const [];
  List<ConversationModel> rows = const [];
  int interactionCount = 0;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => load());
  }

  @override
  void dispose() {
    debounce?.cancel();
    search.dispose();
    super.dispose();
  }

  Future<void> load() async {
    setState(() {
      loading = true;
      error = null;
    });
    try {
      final session = context.read<AppSession>();
      final conversations = await ChatService(session.api).conversations();
      List<Map<String, dynamic>> applications = const [];
      var unreadFollowers = 0;
      try {
        applications = await ChatService(
          session.api,
        ).joinApplications(status: 'PENDING');
      } catch (_) {
        // 互动角标加载失败不应导致整个聊天列表空白。
      }
      try {
        unreadFollowers = await FollowService(
          session.api,
        ).unreadFollowerCount();
      } catch (_) {
        // 未读数接口失败时只隐藏角标，不用历史粉丝总数冒充未读消息。
      }
      if (!mounted) return;
      allRows = List<ConversationModel>.from(conversations);
      interactionCount = applications.length + unreadFollowers;
      _applySearch();
    } catch (e) {
      if (mounted) setState(() => error = e.toString());
    } finally {
      if (mounted) setState(() => loading = false);
    }
  }

  void _applySearch() {
    final keyword = search.text.trim().toLowerCase();
    setState(() {
      rows = keyword.isEmpty
          ? List<ConversationModel>.from(allRows)
          : allRows
                .where(
                  (row) =>
                      row.name.toLowerCase().contains(keyword) ||
                      row.preview.toLowerCase().contains(keyword),
                )
                .toList();
    });
  }

  void onSearch(String _) {
    debounce?.cancel();
    debounce = Timer(const Duration(milliseconds: 220), _applySearch);
    setState(() {});
  }

  Future<void> updateSettings(
    ConversationModel row, {
    bool? pinned,
    bool? muted,
  }) async {
    try {
      await ChatService(context.read<AppSession>().api).updateSettings(
        row.id,
        muted: muted ?? row.muted,
        pinned: pinned ?? row.pinned,
      );
      await load();
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('$e')));
      }
    }
  }

  Future<void> hideConversation(ConversationModel row) async {
    // 用户要求“删除后直接消失”，因此先做乐观移除；接口失败再重新加载恢复。
    setState(() {
      allRows = allRows.where((item) => item.id != row.id).toList();
      rows = rows.where((item) => item.id != row.id).toList();
    });
    try {
      await ChatService(
        context.read<AppSession>().api,
      ).hideConversation(row.id);
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(const SnackBar(content: Text('已从消息列表移除，聊天记录仍保留')));
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('$e')));
        await load();
      }
    }
  }

  Future<void> openConversation(ConversationModel row) async {
    await Navigator.push(
      context,
      MaterialPageRoute(builder: (_) => ChatSessionPage(conversation: row)),
    );
    if (mounted) await load();
  }

  @override
  Widget build(BuildContext context) => SafeArea(
    child: ColoredBox(
      color: Colors.white,
      child: Column(
        children: [
          SizedBox(
            height: 48,
            child: Row(
              children: [
                const SizedBox(width: 10),
                const SizedBox(
                  width: 48,
                  child: Center(
                    child: Icon(
                      LucideIcons.messageCircle,
                      size: 21,
                      color: AppColors.primary,
                    ),
                  ),
                ),
                const Expanded(
                  child: Text(
                    '消息',
                    textAlign: TextAlign.center,
                    style: TextStyle(fontSize: 18, fontWeight: FontWeight.w900),
                  ),
                ),
                IconButton(
                  tooltip: '互动消息',
                  onPressed: () async {
                    await Navigator.push(
                      context,
                      MaterialPageRoute(
                        builder: (_) => const InteractionMessagesPage(),
                      ),
                    );
                    if (mounted) load();
                  },
                  icon: const Icon(LucideIcons.userPlus, size: 21),
                ),
                const SizedBox(width: 6),
              ],
            ),
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(12, 3, 12, 8),
            child: SizedBox(
              height: 38,
              child: TextField(
                controller: search,
                onChanged: onSearch,
                decoration: InputDecoration(
                  hintText: '搜索联系人或群聊',
                  hintStyle: const TextStyle(fontSize: 13),
                  prefixIcon: const Icon(LucideIcons.search, size: 17),
                  suffixIcon: search.text.isEmpty
                      ? null
                      : IconButton(
                          onPressed: () {
                            search.clear();
                            _applySearch();
                          },
                          icon: const Icon(LucideIcons.x, size: 16),
                        ),
                  filled: true,
                  fillColor: const Color(0xFFF3F5F8),
                  contentPadding: EdgeInsets.zero,
                  border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(10),
                    borderSide: BorderSide.none,
                  ),
                ),
              ),
            ),
          ),
          Expanded(
            child: loading && allRows.isEmpty
                ? const Center(child: CircularProgressIndicator())
                : error != null && allRows.isEmpty
                ? Center(
                    child: FilledButton.icon(
                      onPressed: load,
                      icon: const Icon(LucideIcons.refreshCw, size: 17),
                      label: const Text('重新加载'),
                    ),
                  )
                : RefreshIndicator(
                    onRefresh: load,
                    child: ListView(
                      physics: const AlwaysScrollableScrollPhysics(),
                      padding: const EdgeInsets.only(bottom: 22),
                      children: [
                        _InteractionCard(
                          count: interactionCount,
                          onTap: () async {
                            await Navigator.push(
                              context,
                              MaterialPageRoute(
                                builder: (_) => const InteractionMessagesPage(),
                              ),
                            );
                            if (mounted) load();
                          },
                        ),
                        if (rows.isEmpty)
                          Padding(
                            padding: const EdgeInsets.only(top: 90),
                            child: Column(
                              children: [
                                const Icon(
                                  LucideIcons.messageCircle,
                                  size: 44,
                                  color: AppColors.muted,
                                ),
                                const SizedBox(height: 10),
                                Text(
                                  search.text.trim().isEmpty
                                      ? '暂时没有聊天会话'
                                      : '没有匹配的会话',
                                  style: const TextStyle(
                                    color: AppColors.muted,
                                  ),
                                ),
                              ],
                            ),
                          )
                        else
                          ...rows.map(
                            (row) => _SwipeConversationTile(
                              key: ValueKey(row.id),
                              row: row,
                              onOpen: () => openConversation(row),
                              onPin: () =>
                                  updateSettings(row, pinned: !row.pinned),
                              onMute: () =>
                                  updateSettings(row, muted: !row.muted),
                              onDelete: () => hideConversation(row),
                            ),
                          ),
                      ],
                    ),
                  ),
          ),
        ],
      ),
    ),
  );
}

class _InteractionCard extends StatelessWidget {
  const _InteractionCard({required this.count, required this.onTap});
  final int count;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => InkWell(
    onTap: onTap,
    child: Container(
      height: 72,
      padding: const EdgeInsets.symmetric(horizontal: 16),
      color: Colors.white,
      child: Row(
        children: [
          Stack(
            clipBehavior: Clip.none,
            children: [
              Container(
                width: 44,
                height: 44,
                decoration: const BoxDecoration(
                  color: AppColors.primary,
                  shape: BoxShape.circle,
                ),
                child: const Icon(
                  LucideIcons.heart,
                  color: Colors.white,
                  size: 21,
                ),
              ),
              if (count > 0)
                Positioned(
                  right: -4,
                  top: -4,
                  child: Container(
                    constraints: const BoxConstraints(
                      minWidth: 18,
                      minHeight: 18,
                    ),
                    padding: const EdgeInsets.symmetric(horizontal: 5),
                    alignment: Alignment.center,
                    decoration: const BoxDecoration(
                      color: AppColors.danger,
                      shape: BoxShape.circle,
                    ),
                    child: Text(
                      count > 99 ? '99+' : '$count',
                      style: const TextStyle(
                        color: Colors.white,
                        fontSize: 9,
                        fontWeight: FontWeight.w800,
                      ),
                    ),
                  ),
                ),
            ],
          ),
          const SizedBox(width: 12),
          const Expanded(
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  '互动消息',
                  style: TextStyle(fontSize: 15.5, fontWeight: FontWeight.w900),
                ),
                SizedBox(height: 3),
                Text(
                  '入队申请、谁关注了我和互动提醒',
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(fontSize: 12, color: AppColors.muted),
                ),
              ],
            ),
          ),
          const Icon(
            LucideIcons.chevronRight,
            size: 18,
            color: AppColors.muted,
          ),
        ],
      ),
    ),
  );
}

class _SwipeConversationTile extends StatefulWidget {
  const _SwipeConversationTile({
    required this.row,
    required this.onOpen,
    required this.onPin,
    required this.onMute,
    required this.onDelete,
    super.key,
  });
  final ConversationModel row;
  final Future<void> Function() onOpen;
  final Future<void> Function() onPin;
  final Future<void> Function() onMute;
  final Future<void> Function() onDelete;

  @override
  State<_SwipeConversationTile> createState() => _SwipeConversationTileState();
}

class _SwipeConversationTileState extends State<_SwipeConversationTile> {
  static const actionWidth = 78.0;
  static const totalWidth = actionWidth * 3;
  double offset = 0;

  void settle() => setState(() => offset = offset < -52 ? -totalWidth : 0);

  @override
  Widget build(BuildContext context) => SizedBox(
    height: 74,
    child: ClipRect(
      child: Stack(
        fit: StackFit.expand,
        children: [
          Align(
            alignment: Alignment.centerRight,
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                _SwipeAction(
                  width: actionWidth,
                  color: const Color(0xFF7E8795),
                  icon: widget.row.pinned
                      ? LucideIcons.pinOff
                      : LucideIcons.pin,
                  label: widget.row.pinned ? '取消置顶' : '置顶',
                  onTap: widget.onPin,
                ),
                _SwipeAction(
                  width: actionWidth,
                  color: const Color(0xFFF59E0B),
                  icon: widget.row.muted
                      ? LucideIcons.bell
                      : LucideIcons.bellOff,
                  label: widget.row.muted ? '取消免扰' : '免打扰',
                  onTap: widget.onMute,
                ),
                _SwipeAction(
                  width: actionWidth,
                  color: AppColors.danger,
                  icon: LucideIcons.trash2,
                  label: '删除聊天',
                  onTap: widget.onDelete,
                ),
              ],
            ),
          ),
          AnimatedContainer(
            duration: const Duration(milliseconds: 170),
            curve: Curves.easeOutCubic,
            transform: Matrix4.translationValues(offset, 0, 0),
            child: GestureDetector(
              onHorizontalDragUpdate: (details) => setState(
                () => offset = (offset + details.delta.dx)
                    .clamp(-totalWidth, 0)
                    .toDouble(),
              ),
              onHorizontalDragEnd: (_) => settle(),
              child: Material(
                color: widget.row.pinned
                    ? const Color(0xFFF4F7FC)
                    : Colors.white,
                child: InkWell(
                  onTap: () {
                    if (offset < 0) {
                      setState(() => offset = 0);
                    } else {
                      widget.onOpen();
                    }
                  },
                  child: Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 14),
                    child: Row(
                      children: [
                        UserAvatar(
                          nickname: widget.row.name,
                          avatarImageKey: widget.row.avatarImageKey,
                          avatarUrl: widget.row.avatarUrl,
                          radius: 21,
                        ),
                        const SizedBox(width: 11),
                        Expanded(
                          child: Column(
                            mainAxisAlignment: MainAxisAlignment.center,
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Row(
                                children: [
                                  Expanded(
                                    child: Text(
                                      widget.row.name,
                                      maxLines: 1,
                                      overflow: TextOverflow.ellipsis,
                                      style: const TextStyle(
                                        fontSize: 15.5,
                                        fontWeight: FontWeight.w800,
                                      ),
                                    ),
                                  ),
                                  if (widget.row.muted)
                                    const Padding(
                                      padding: EdgeInsets.only(right: 5),
                                      child: Icon(
                                        LucideIcons.bellOff,
                                        size: 13,
                                        color: AppColors.muted,
                                      ),
                                    ),
                                  Text(
                                    _formatTime(widget.row.time),
                                    style: const TextStyle(
                                      fontSize: 11,
                                      color: AppColors.muted,
                                    ),
                                  ),
                                ],
                              ),
                              const SizedBox(height: 5),
                              Row(
                                children: [
                                  Expanded(
                                    child: Text(
                                      widget.row.preview.isEmpty
                                          ? '暂无消息'
                                          : widget.row.preview,
                                      maxLines: 1,
                                      overflow: TextOverflow.ellipsis,
                                      style: const TextStyle(
                                        fontSize: 12.5,
                                        color: AppColors.muted,
                                      ),
                                    ),
                                  ),
                                  if (widget.row.unread > 0)
                                    Container(
                                      constraints: const BoxConstraints(
                                        minWidth: 18,
                                        minHeight: 18,
                                      ),
                                      padding: const EdgeInsets.symmetric(
                                        horizontal: 5,
                                      ),
                                      alignment: Alignment.center,
                                      decoration: const BoxDecoration(
                                        color: AppColors.primary,
                                        shape: BoxShape.circle,
                                      ),
                                      child: Text(
                                        widget.row.unread > 99
                                            ? '99+'
                                            : '${widget.row.unread}',
                                        style: const TextStyle(
                                          color: Colors.white,
                                          fontSize: 9,
                                          fontWeight: FontWeight.w800,
                                        ),
                                      ),
                                    ),
                                ],
                              ),
                            ],
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
    ),
  );
}

class _SwipeAction extends StatelessWidget {
  const _SwipeAction({
    required this.width,
    required this.color,
    required this.icon,
    required this.label,
    required this.onTap,
  });
  final double width;
  final Color color;
  final IconData icon;
  final String label;
  final Future<void> Function() onTap;

  @override
  Widget build(BuildContext context) => SizedBox(
    width: width,
    child: Material(
      color: color,
      child: InkWell(
        onTap: () async {
          await onTap();
        },
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(icon, size: 18, color: Colors.white),
            const SizedBox(height: 4),
            Text(
              label,
              style: const TextStyle(
                color: Colors.white,
                fontSize: 10.5,
                fontWeight: FontWeight.w700,
              ),
            ),
          ],
        ),
      ),
    ),
  );
}

String _formatTime(String value) {
  final parsed = DateTime.tryParse(value)?.toLocal();
  if (parsed == null) return value.length > 10 ? value.substring(0, 10) : value;
  final now = DateTime.now();
  if (parsed.year == now.year &&
      parsed.month == now.month &&
      parsed.day == now.day) {
    return '${parsed.hour.toString().padLeft(2, '0')}:${parsed.minute.toString().padLeft(2, '0')}';
  }
  if (parsed.year == now.year) return '${parsed.month}/${parsed.day}';
  return '${parsed.year}/${parsed.month}/${parsed.day}';
}
