import 'dart:async';

import 'package:flutter/material.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';
import 'package:provider/provider.dart';

import '../../../app/app_session.dart';
import '../../../app/theme.dart';
import '../../../common/widgets/app_widgets.dart';
import '../../../data/models/app_models.dart';
import '../../../data/services/app_services.dart';
import 'chat_management_pages.dart';
import 'chat_session_page.dart';

class ChatIndexPage extends StatefulWidget {
  const ChatIndexPage({super.key});

  @override
  State<ChatIndexPage> createState() => _ChatIndexPageState();
}

class _ChatIndexPageState extends State<ChatIndexPage> {
  final searchController = TextEditingController();
  Timer? searchDebounce;
  bool loading = true;
  bool searching = false;
  String? error;
  String keyword = '';
  int requestSerial = 0;
  List<ConversationModel> allRows = [];
  List<ConversationModel> rows = [];

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => load());
  }

  @override
  void dispose() {
    searchDebounce?.cancel();
    searchController.dispose();
    super.dispose();
  }

  void onSearchChanged(String value) {
    keyword = value.trim();
    requestSerial++;
    searchDebounce?.cancel();
    searchDebounce = Timer(
      const Duration(milliseconds: 280),
      () => load(showLoading: false),
    );
    setState(() {
      rows = _filterByTitle(allRows, keyword);
      searching = true;
      error = null;
    });
  }

  Future<void> load({bool showLoading = true}) async {
    final requestId = ++requestSerial;
    setState(() {
      if (showLoading) loading = true;
      searching = !showLoading;
    });
    try {
      final result = await ChatService(
        context.read<AppSession>().api,
      ).conversations(title: keyword);
      if (requestId != requestSerial) return;
      rows = result;
      if (keyword.isEmpty) allRows = result;
      error = null;
    } catch (e) {
      if (requestId != requestSerial) return;
      error = e.toString();
    }
    if (mounted && requestId == requestSerial) {
      setState(() {
        loading = false;
        searching = false;
      });
    }
  }

  List<ConversationModel> _filterByTitle(
    List<ConversationModel> source,
    String title,
  ) {
    final normalized = title.toLowerCase();
    if (normalized.isEmpty) return List<ConversationModel>.from(source);
    return source
        .where((row) => row.name.toLowerCase().contains(normalized))
        .toList();
  }

  Future<void> togglePin(ConversationModel row) async {
    await ChatService(
      context.read<AppSession>().api,
    ).updateSettings(row.id, muted: row.muted, pinned: !row.pinned);
    await load(showLoading: false);
  }

  Future<void> openConversation(ConversationModel row) async {
    await Navigator.push(
      context,
      MaterialPageRoute(builder: (_) => ChatSessionPage(conversation: row)),
    );
    if (mounted) await load(showLoading: false);
  }

  @override
  Widget build(BuildContext context) => SafeArea(
    child: ColoredBox(
      color: Colors.white,
      child: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(22, 18, 18, 0),
            child: Row(
              children: [
                const Expanded(
                  child: Text(
                    '消息',
                    style: TextStyle(
                      fontSize: 32,
                      height: 1.2,
                      fontWeight: FontWeight.w900,
                      letterSpacing: -.5,
                    ),
                  ),
                ),
                IconButton.filledTonal(
                  tooltip: '入队申请',
                  onPressed: () => Navigator.push(
                    context,
                    MaterialPageRoute(
                      builder: (_) => const JoinApplicationsPage(),
                    ),
                  ),
                  icon: const Icon(LucideIcons.userPlus, size: 20),
                ),
              ],
            ),
          ),
          const SizedBox(height: 16),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 22),
            child: SizedBox(
              height: 40,
              child: TextField(
                controller: searchController,
                onChanged: onSearchChanged,
                textInputAction: TextInputAction.search,
                onSubmitted: (_) => load(showLoading: false),
                decoration: InputDecoration(
                  isDense: true,
                  hintText: '搜索会话标题',
                  hintStyle: const TextStyle(
                    fontSize: 13,
                    color: AppColors.muted,
                  ),
                  prefixIcon: const Icon(LucideIcons.search, size: 18),
                  prefixIconConstraints: const BoxConstraints(minWidth: 42),
                  suffixIcon: keyword.isEmpty
                      ? null
                      : IconButton(
                          tooltip: '清除搜索',
                          onPressed: () {
                            searchController.clear();
                            onSearchChanged('');
                          },
                          icon: const Icon(LucideIcons.x, size: 17),
                        ),
                  filled: true,
                  fillColor: const Color(0xFFF0F5FC),
                  contentPadding: const EdgeInsets.symmetric(vertical: 9),
                  border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(20),
                    borderSide: BorderSide.none,
                  ),
                ),
              ),
            ),
          ),
          SizedBox(
            height: 3,
            child: searching
                ? const LinearProgressIndicator(minHeight: 2)
                : null,
          ),
          const SizedBox(height: 7),
          Expanded(
            child: AsyncPanel(
              loading: loading,
              error: error,
              onRetry: load,
              child: RefreshIndicator(
                onRefresh: load,
                child: rows.isEmpty
                    ? _EmptyMessages(searchingTitle: keyword.isNotEmpty)
                    : ListView.separated(
                        keyboardDismissBehavior:
                            ScrollViewKeyboardDismissBehavior.onDrag,
                        padding: const EdgeInsets.fromLTRB(16, 0, 16, 28),
                        itemCount: rows.length,
                        separatorBuilder: (_, _) => const Divider(
                          height: 1,
                          indent: 78,
                          color: Color(0xFFE9EDF3),
                        ),
                        itemBuilder: (context, index) {
                          final row = rows[index];
                          return _SwipeConversationTile(
                            key: ValueKey('conversation-${row.id}'),
                            row: row,
                            time: _formatTime(row.time),
                            onPin: () => togglePin(row),
                            onOpen: () => openConversation(row),
                          );
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

class _EmptyMessages extends StatelessWidget {
  const _EmptyMessages({required this.searchingTitle});
  final bool searchingTitle;

  @override
  Widget build(BuildContext context) => ListView(
    padding: const EdgeInsets.only(top: 116),
    children: [
      const Icon(LucideIcons.messageCircle, size: 52, color: AppColors.muted),
      const SizedBox(height: 14),
      Text(
        searchingTitle ? '没有匹配的会话标题' : '暂时没有行程群聊',
        textAlign: TextAlign.center,
        style: const TextStyle(fontWeight: FontWeight.w800),
      ),
      const SizedBox(height: 6),
      Text(
        searchingTitle ? '换一个标题关键词试试' : '发布行程或加入车队后，可在这里与同行成员沟通',
        textAlign: TextAlign.center,
        style: const TextStyle(color: AppColors.muted),
      ),
    ],
  );
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
  static const actionWidth = 88.0;
  double offset = 0;

  void settle() => setState(() => offset = offset < -38 ? -actionWidth : 0);

  @override
  Widget build(BuildContext context) => SizedBox(
    height: 92,
    child: ClipRect(
      child: Stack(
        fit: StackFit.expand,
        children: [
          Align(
            alignment: Alignment.centerRight,
            child: SizedBox(
              width: actionWidth,
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
                        size: 20,
                        color: Colors.white,
                      ),
                      const SizedBox(height: 5),
                      Text(
                        widget.row.pinned ? '取消置顶' : '置顶',
                        style: const TextStyle(
                          fontSize: 12,
                          color: Colors.white,
                          fontWeight: FontWeight.w700,
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
              onHorizontalDragUpdate: (details) => setState(
                () =>
                    offset = (offset + details.delta.dx).clamp(-actionWidth, 0),
              ),
              onHorizontalDragEnd: (_) => settle(),
              child: Material(
                color: widget.row.pinned
                    ? const Color(0xFFF2F6FC)
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
                    padding: const EdgeInsets.symmetric(horizontal: 6),
                    child: Row(
                      children: [
                        _ConversationAvatar(name: widget.row.name),
                        const SizedBox(width: 14),
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
                                        fontSize: 16,
                                        fontWeight: FontWeight.w800,
                                      ),
                                    ),
                                  ),
                                  if (widget.row.pinned)
                                    const Padding(
                                      padding: EdgeInsets.only(left: 5),
                                      child: Icon(
                                        LucideIcons.pin,
                                        size: 13,
                                        color: AppColors.muted,
                                      ),
                                    ),
                                  const SizedBox(width: 8),
                                  Text(
                                    widget.time,
                                    style: const TextStyle(
                                      fontSize: 12,
                                      color: AppColors.muted,
                                    ),
                                  ),
                                ],
                              ),
                              const SizedBox(height: 7),
                              Row(
                                children: [
                                  Expanded(
                                    child: Text(
                                      widget.row.preview.isEmpty
                                          ? '暂无消息'
                                          : widget.row.preview,
                                      maxLines: 2,
                                      overflow: TextOverflow.ellipsis,
                                      style: const TextStyle(
                                        height: 1.25,
                                        color: AppColors.secondaryText,
                                      ),
                                    ),
                                  ),
                                  if (widget.row.unread > 0) ...[
                                    const SizedBox(width: 8),
                                    Container(
                                      constraints: const BoxConstraints(
                                        minWidth: 20,
                                        minHeight: 20,
                                      ),
                                      padding: const EdgeInsets.symmetric(
                                        horizontal: 6,
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
                                          fontSize: 10,
                                          fontWeight: FontWeight.w700,
                                        ),
                                      ),
                                    ),
                                  ],
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

class _ConversationAvatar extends StatelessWidget {
  const _ConversationAvatar({required this.name});
  final String name;

  @override
  Widget build(BuildContext context) => Container(
    width: 58,
    height: 58,
    decoration: const BoxDecoration(
      gradient: LinearGradient(
        begin: Alignment.topLeft,
        end: Alignment.bottomRight,
        colors: [Color(0xFFDCEBFF), Color(0xFFAED2FF)],
      ),
      shape: BoxShape.circle,
    ),
    alignment: Alignment.center,
    child: Text(
      name.isEmpty ? '聊' : name.characters.first,
      style: const TextStyle(
        color: AppColors.primaryDark,
        fontSize: 20,
        fontWeight: FontWeight.w800,
      ),
    ),
  );
}
