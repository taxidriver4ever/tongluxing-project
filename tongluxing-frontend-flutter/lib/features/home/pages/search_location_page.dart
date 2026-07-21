import 'dart:async';

import 'package:flutter/material.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';
import 'package:provider/provider.dart';

import '../../../app/app_session.dart';
import '../../../app/theme.dart';
import '../../../data/models/app_models.dart';
import '../../../data/services/api_client.dart';
import '../../../data/services/app_services.dart';

class SearchLocationPage extends StatefulWidget {
  const SearchLocationPage({
    super.key,
    this.originLatitude,
    this.originLongitude,
  });

  final double? originLatitude;
  final double? originLongitude;

  @override
  State<SearchLocationPage> createState() => _SearchLocationPageState();
}

class _SearchLocationPageState extends State<SearchLocationPage> {
  final _controller = TextEditingController();
  final _focusNode = FocusNode();
  Timer? _debounce;
  LocationService? _service;
  List<LocationSelection> _results = const [];
  List<LocationSelection> _history = const [];
  bool _loading = true;
  bool _selecting = false;
  bool _updatingHistory = false;
  String? _error;

  bool get _hasKeyword => _controller.text.trim().isNotEmpty;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_service != null) return;
    _service = LocationService(context.read<AppSession>().api);
    _loadHistory();
  }

  Future<bool> _loadHistory() async {
    try {
      final history = await _service!.history(
        latitude: widget.originLatitude,
        longitude: widget.originLongitude,
      );
      if (!mounted) return false;
      setState(() {
        // 最近搜索以后端数据库回读为唯一真源，不在前端补造或去重。
        _history = history;
        _loading = false;
      });
      return true;
    } on ApiException catch (error) {
      if (!mounted) return false;
      setState(() {
        _error = error.message;
        _loading = false;
      });
      return false;
    }
  }

  void _onKeywordChanged(String value) {
    setState(() {
      _error = null;
      if (value.trim().isEmpty) _results = const [];
    });
    _debounce?.cancel();
    if (value.trim().isEmpty) return;
    _debounce = Timer(const Duration(milliseconds: 300), () => _search(value));
  }

  Future<void> _search(String keyword) async {
    final query = keyword.trim();
    if (query.isEmpty) return;
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final results = await _service!.search(
        query,
        latitude: widget.originLatitude,
        longitude: widget.originLongitude,
      );
      if (!mounted || query != _controller.text.trim()) return;
      results.sort(
        (left, right) =>
            _relevance(left, query).compareTo(_relevance(right, query)),
      );
      setState(() {
        _results = results;
        _loading = false;
      });
    } on ApiException catch (error) {
      if (!mounted || query != _controller.text.trim()) return;
      setState(() {
        _error = error.message;
        _loading = false;
      });
    }
  }

  int _relevance(LocationSelection location, String keyword) {
    final name = location.name.toLowerCase();
    final address = location.address.toLowerCase();
    final query = keyword.toLowerCase();
    if (name == query) return 0;
    if (name.startsWith(query)) return 1;
    if (name.contains(query)) return 2;
    if (address.startsWith(query)) return 3;
    if (address.contains(query)) return 4;
    return 5;
  }

  void _clearKeyword() {
    _debounce?.cancel();
    _controller.clear();
    setState(() {
      _results = const [];
      _error = null;
      _loading = false;
    });
    _focusNode.requestFocus();
  }

  Future<void> _select(LocationSelection location) async {
    FocusScope.of(context).unfocus();
    setState(() => _selecting = true);
    try {
      final saved = await _service!.select(location);
      if (!mounted) return;
      Navigator.pop(context, saved);
    } on ApiException catch (error) {
      if (!mounted) return;
      setState(() => _selecting = false);
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(error.message)));
    }
  }

  Future<void> _deleteHistory(LocationSelection location) async {
    final historyId = location.historyId;
    if (historyId == null || _updatingHistory) return;
    setState(() => _updatingHistory = true);
    try {
      final affected = await _service!.deleteHistory(historyId);
      if (affected != 1) {
        throw const ApiException('后端未删除该条搜索记录');
      }
      // 删除后必须重新查询后端，禁止只在本地 List 中移除造成假删除。
      if (!await _loadHistory()) {
        throw const ApiException('删除已写入后端，但重新读取失败，请重新进入页面确认');
      }
      if (!mounted) return;
      setState(() => _updatingHistory = false);
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('已删除该条搜索记录')));
    } on ApiException catch (error) {
      if (!mounted) return;
      setState(() => _updatingHistory = false);
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(error.message)));
    }
  }

  Future<void> _clearHistory() async {
    FocusScope.of(context).unfocus();
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('清空最近搜索？'),
        content: const Text('清空后无法恢复，但不会影响已创建的行程。'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext, false),
            child: const Text('取消'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(dialogContext, true),
            child: const Text('清空'),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted || _updatingHistory) return;
    setState(() => _updatingHistory = true);
    try {
      final affected = await _service!.clearHistory();
      // 清空后同样回读数据库；即使 affected=0，幂等结果也必须由 GET 确认。
      if (!await _loadHistory()) {
        throw const ApiException('清空已写入后端，但重新读取失败，请重新进入页面确认');
      }
      if (!mounted) return;
      setState(() => _updatingHistory = false);
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text('最近搜索已清空（后端删除 $affected 条）')));
    } on ApiException catch (error) {
      if (!mounted) return;
      setState(() => _updatingHistory = false);
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(error.message)));
    }
  }

  @override
  void dispose() {
    _debounce?.cancel();
    _controller.dispose();
    _focusNode.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    backgroundColor: Colors.white,
    body: SafeArea(
      child: Stack(
        children: [
          Column(
            children: [
              _SearchHeader(
                controller: _controller,
                focusNode: _focusNode,
                hasKeyword: _hasKeyword,
                onBack: () => Navigator.pop(context),
                onChanged: _onKeywordChanged,
                onSubmitted: _search,
                onClear: _clearKeyword,
              ),
              const Divider(height: 8, thickness: 8, color: Color(0xFFF5F7FA)),
              Expanded(child: _buildContent()),
            ],
          ),
          if (_selecting)
            const Positioned.fill(
              child: ColoredBox(
                color: Color(0x33000000),
                child: Center(child: CircularProgressIndicator()),
              ),
            ),
        ],
      ),
    ),
  );

  Widget _buildContent() {
    final locations = _hasKeyword ? _results : _history;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(30, 18, 24, 10),
          child: Row(
            children: [
              Text(
                _hasKeyword ? '搜索建议' : '最近搜索',
                style: const TextStyle(
                  fontSize: 18,
                  fontWeight: FontWeight.w700,
                ),
              ),
              const Spacer(),
              if (!_hasKeyword && _history.isNotEmpty)
                TextButton.icon(
                  onPressed: _updatingHistory ? null : _clearHistory,
                  icon: const Icon(LucideIcons.trash2, size: 17),
                  label: const Text('清空'),
                  style: TextButton.styleFrom(
                    foregroundColor: const Color(0xFF667085),
                  ),
                ),
            ],
          ),
        ),
        if (_loading) const LinearProgressIndicator(minHeight: 2),
        if (_error case final message?)
          Expanded(
            child: _EmptyState(
              message: message,
              actionText: '重试',
              onAction: () =>
                  _hasKeyword ? _search(_controller.text) : _loadHistory(),
            ),
          )
        else if (!_loading && locations.isEmpty)
          Expanded(
            child: _EmptyState(message: _hasKeyword ? '没有找到相关地点' : '暂无最近搜索记录'),
          )
        else
          Expanded(
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 30),
              child: ListView.separated(
                keyboardDismissBehavior:
                    ScrollViewKeyboardDismissBehavior.onDrag,
                padding: const EdgeInsets.only(bottom: 24),
                itemCount: locations.length,
                separatorBuilder: (_, _) => _hasKeyword
                    ? const Divider(
                        height: 1,
                        indent: 58,
                        color: Color(0xFFEAECF0),
                      )
                    : const SizedBox(height: 9),
                itemBuilder: (context, index) => _LocationRow(
                  location: locations[index],
                  history: !_hasKeyword,
                  onTap: () => _select(locations[index]),
                  onDelete: !_hasKeyword
                      ? () => _deleteHistory(locations[index])
                      : null,
                ),
              ),
            ),
          ),
      ],
    );
  }
}

class _SearchHeader extends StatelessWidget {
  const _SearchHeader({
    required this.controller,
    required this.focusNode,
    required this.hasKeyword,
    required this.onBack,
    required this.onChanged,
    required this.onSubmitted,
    required this.onClear,
  });

  final TextEditingController controller;
  final FocusNode focusNode;
  final bool hasKeyword;
  final VoidCallback onBack;
  final ValueChanged<String> onChanged;
  final ValueChanged<String> onSubmitted;
  final VoidCallback onClear;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.fromLTRB(24, 14, 24, 16),
    child: Container(
      height: 58,
      decoration: BoxDecoration(
        color: const Color(0xFFF2F4F7),
        borderRadius: BorderRadius.circular(29),
      ),
      child: Row(
        children: [
          IconButton(
            onPressed: onBack,
            icon: const Icon(LucideIcons.chevronLeft, size: 30),
          ),
          Expanded(
            child: TextField(
              controller: controller,
              focusNode: focusNode,
              autofocus: false,
              textInputAction: TextInputAction.search,
              onChanged: onChanged,
              onSubmitted: onSubmitted,
              style: const TextStyle(fontSize: 18),
              decoration: const InputDecoration(
                hintText: '在此处搜索',
                border: InputBorder.none,
                enabledBorder: InputBorder.none,
                focusedBorder: InputBorder.none,
                filled: false,
                contentPadding: EdgeInsets.only(left: 14, right: 4),
              ),
            ),
          ),
          if (hasKeyword)
            IconButton(
              onPressed: onClear,
              icon: const Icon(
                LucideIcons.circleX,
                size: 25,
                color: Color(0xFF344054),
              ),
            )
          else
            const SizedBox(width: 18),
          const SizedBox(width: 6),
        ],
      ),
    ),
  );
}

class _LocationRow extends StatelessWidget {
  const _LocationRow({
    required this.location,
    required this.history,
    required this.onTap,
    this.onDelete,
  });

  final LocationSelection location;
  final bool history;
  final VoidCallback onTap;
  final VoidCallback? onDelete;

  @override
  Widget build(BuildContext context) {
    final content = Padding(
      padding: EdgeInsets.fromLTRB(12, history ? 10 : 11, 8, history ? 10 : 11),
      child: Row(
        children: [
          Container(
            width: 38,
            height: 38,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              color: history ? Colors.white : const Color(0xFFF2F4F7),
            ),
            child: Icon(
              history ? LucideIcons.history : LucideIcons.mapPin,
              size: 19,
              color: history ? const Color(0xFF475467) : AppColors.primary,
            ),
          ),
          const SizedBox(width: 13),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  location.name,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    fontSize: 15,
                    fontWeight: FontWeight.w600,
                  ),
                ),
                if (location.address.isNotEmpty) ...[
                  const SizedBox(height: 3),
                  Text(
                    location.address,
                    maxLines: history ? 1 : 2,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      fontSize: 12,
                      color: Color(0xFF667085),
                    ),
                  ),
                ],
                if (location.distanceLabel case final distance?) ...[
                  const SizedBox(height: 4),
                  Text(
                    '距离 $distance',
                    style: const TextStyle(
                      fontSize: 12,
                      color: AppColors.primary,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ],
              ],
            ),
          ),
          const SizedBox(width: 6),
          if (onDelete case final delete?)
            IconButton(
              tooltip: '删除搜索记录',
              visualDensity: VisualDensity.compact,
              onPressed: delete,
              icon: const Icon(
                LucideIcons.x,
                size: 19,
                color: Color(0xFF98A2B3),
              ),
            )
          else
            const Padding(
              padding: EdgeInsets.only(right: 8),
              child: Icon(
                LucideIcons.arrowUpLeft,
                size: 19,
                color: Color(0xFF667085),
              ),
            ),
        ],
      ),
    );

    return Material(
      color: history ? const Color(0xFFF7F9FC) : Colors.transparent,
      borderRadius: BorderRadius.circular(16),
      clipBehavior: Clip.antiAlias,
      child: InkWell(onTap: onTap, child: content),
    );
  }
}

class _EmptyState extends StatelessWidget {
  const _EmptyState({this.message = '', this.actionText, this.onAction});

  final String message;
  final String? actionText;
  final VoidCallback? onAction;

  @override
  Widget build(BuildContext context) => Center(
    child: Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        const Icon(LucideIcons.mapPin, size: 34, color: Color(0xFF98A2B3)),
        const SizedBox(height: 10),
        Text(message, style: const TextStyle(color: Color(0xFF667085))),
        if (actionText != null)
          TextButton(onPressed: onAction, child: Text(actionText!)),
      ],
    ),
  );
}
