import 'package:flutter/material.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';
import 'package:provider/provider.dart';

import '../../../app/app_session.dart';
import '../../../app/theme.dart';
import '../../../data/services/app_services.dart';

/// Displays a user avatar and resolves a fresh MinIO download URL from the
/// stored object key when necessary.
class UserAvatar extends StatefulWidget {
  const UserAvatar({
    required this.nickname,
    this.avatarImageKey = '',
    this.avatarUrl = '',
    this.radius = 20,
    this.onTap,
    this.heroTag,
    super.key,
  });

  final String nickname;
  final String avatarImageKey;
  final String avatarUrl;
  final double radius;
  final VoidCallback? onTap;
  final Object? heroTag;

  @override
  State<UserAvatar> createState() => _UserAvatarState();
}

class _UserAvatarState extends State<UserAvatar> {
  static final Map<String, Future<String>> _urlCache = {};

  String resolvedUrl = '';
  String loadedKey = '';
  bool resolving = false;
  bool retriedAfterImageError = false;

  @override
  void initState() {
    super.initState();
    resolvedUrl = widget.avatarUrl.trim();
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _resolveFromObjectKey();
  }

  @override
  void didUpdateWidget(covariant UserAvatar oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.avatarUrl != widget.avatarUrl ||
        oldWidget.avatarImageKey != widget.avatarImageKey) {
      resolvedUrl = widget.avatarUrl.trim();
      loadedKey = '';
      resolving = false;
      retriedAfterImageError = false;
      _resolveFromObjectKey();
    }
  }

  Future<void> _resolveFromObjectKey({bool force = false}) async {
    final objectKey = widget.avatarImageKey.trim();
    if (objectKey.isEmpty || objectKey.startsWith('data:')) return;
    if (!force && (resolving || loadedKey == objectKey)) return;

    resolving = true;
    loadedKey = objectKey;
    try {
      if (force) _urlCache.remove(objectKey);
      final future = _urlCache.putIfAbsent(
        objectKey,
        () => StorageUploadService(
          context.read<AppSession>().api,
        ).downloadUrlByObjectKey(objectKey),
      );
      final next = (await future).trim();
      if (mounted && next.isNotEmpty) {
        setState(() => resolvedUrl = next);
      }
    } catch (_) {
      _urlCache.remove(objectKey);
    } finally {
      resolving = false;
    }
  }

  Widget _fallback() {
    final text = widget.nickname.trim();
    return ColoredBox(
      color: AppColors.primarySoft,
      child: Center(
        child: text.isEmpty
            ? Icon(
                LucideIcons.userRound,
                size: widget.radius,
                color: AppColors.primary,
              )
            : Text(
                text.characters.first,
                style: TextStyle(
                  fontSize: widget.radius * .82,
                  color: AppColors.primary,
                  fontWeight: FontWeight.w800,
                ),
              ),
      ),
    );
  }

  Widget _avatar() {
    final size = widget.radius * 2;
    final image = resolvedUrl.isEmpty
        ? _fallback()
        : Image.network(
            resolvedUrl,
            width: size,
            height: size,
            fit: BoxFit.cover,
            gaplessPlayback: true,
            errorBuilder: (_, _, _) {
              if (!retriedAfterImageError) {
                retriedAfterImageError = true;
                WidgetsBinding.instance.addPostFrameCallback((_) {
                  if (mounted && widget.avatarImageKey.trim().isNotEmpty) {
                    _resolveFromObjectKey(force: true);
                  }
                });
              }
              return _fallback();
            },
          );

    final clipped = ClipOval(
      child: SizedBox(width: size, height: size, child: image),
    );
    return widget.heroTag == null
        ? clipped
        : Hero(tag: widget.heroTag!, child: clipped);
  }

  @override
  Widget build(BuildContext context) => Material(
    color: Colors.transparent,
    shape: const CircleBorder(),
    child: InkWell(
      customBorder: const CircleBorder(),
      onTap: widget.onTap,
      child: _avatar(),
    ),
  );
}

/// Full-screen user avatar preview. The owner can continue to the edit page by
/// using the action button supplied by the profile page.
class AvatarPreviewPage extends StatefulWidget {
  const AvatarPreviewPage({
    required this.nickname,
    this.avatarImageKey = '',
    this.avatarUrl = '',
    this.canEdit = false,
    this.onEdit,
    super.key,
  });

  final String nickname;
  final String avatarImageKey;
  final String avatarUrl;
  final bool canEdit;
  final Future<bool> Function()? onEdit;

  @override
  State<AvatarPreviewPage> createState() => _AvatarPreviewPageState();
}

class _AvatarPreviewPageState extends State<AvatarPreviewPage> {
  String resolvedUrl = '';
  bool loading = false;

  @override
  void initState() {
    super.initState();
    resolvedUrl = widget.avatarUrl.trim();
    WidgetsBinding.instance.addPostFrameCallback((_) => _loadFreshUrl());
  }

  Future<void> _loadFreshUrl() async {
    final objectKey = widget.avatarImageKey.trim();
    if (objectKey.isEmpty || objectKey.startsWith('data:')) return;
    setState(() => loading = true);
    try {
      final next = await StorageUploadService(
        context.read<AppSession>().api,
      ).downloadUrlByObjectKey(objectKey);
      if (mounted && next.trim().isNotEmpty) {
        setState(() => resolvedUrl = next.trim());
      }
    } catch (_) {
      // Keep the provided URL or the fallback avatar.
    } finally {
      if (mounted) setState(() => loading = false);
    }
  }

  Future<void> _edit() async {
    final changed = await widget.onEdit?.call() ?? false;
    if (changed && mounted) Navigator.pop(context, true);
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    backgroundColor: const Color(0xFF101216),
    appBar: AppBar(
      title: const Text('头像'),
      foregroundColor: Colors.white,
      backgroundColor: Colors.black.withValues(alpha: .55),
      actions: [
        if (widget.canEdit)
          TextButton.icon(
            onPressed: _edit,
            icon: const Icon(LucideIcons.pencil, color: Colors.white, size: 18),
            label: const Text('修改', style: TextStyle(color: Colors.white)),
          ),
      ],
    ),
    body: Stack(
      children: [
        Positioned.fill(
          child: resolvedUrl.isEmpty
              ? Center(
                  child: UserAvatar(
                    nickname: widget.nickname,
                    avatarImageKey: widget.avatarImageKey,
                    radius: 72,
                  ),
                )
              : InteractiveViewer(
                  minScale: .8,
                  maxScale: 5,
                  child: Center(
                    child: Image.network(
                      resolvedUrl,
                      fit: BoxFit.contain,
                      errorBuilder: (_, _, _) => UserAvatar(
                        nickname: widget.nickname,
                        avatarImageKey: widget.avatarImageKey,
                        radius: 72,
                      ),
                    ),
                  ),
                ),
        ),
        if (loading)
          const Positioned(
            left: 0,
            right: 0,
            bottom: 30,
            child: Center(
              child: CircularProgressIndicator(color: Colors.white),
            ),
          ),
      ],
    ),
  );
}
