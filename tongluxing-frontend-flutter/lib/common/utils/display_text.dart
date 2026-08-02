/// 将过长昵称压缩为适合列表、卡片和详情标题展示的形式。
///
/// 中文昵称最多显示 6 个字符，纯英文/数字昵称最多显示 8 个字符。超过后
/// 同时保留头尾，例如 `abcdefghijk` 显示为 `abcd…ijk`。这里只改变 UI
/// 展示文本，编辑昵称页面和服务端保存的真实昵称不受影响。
String compactDisplayName(String? raw, {String fallback = '同路行用户'}) {
  final value = raw?.trim() ?? '';
  if (value.isEmpty) return fallback;
  final runes = value.runes.toList(growable: false);
  final containsWideCharacter = runes.any(_isWideRune);
  final limit = containsWideCharacter ? 6 : 8;
  if (runes.length <= limit) return value;

  final headLength = containsWideCharacter ? 3 : 4;
  final tailLength = containsWideCharacter ? 2 : 3;
  final head = String.fromCharCodes(runes.take(headLength));
  final tail = String.fromCharCodes(runes.skip(runes.length - tailLength));
  return '$head…$tail';
}

bool _isWideRune(int rune) =>
    (rune >= 0x2E80 && rune <= 0x9FFF) ||
    (rune >= 0xF900 && rune <= 0xFAFF) ||
    (rune >= 0x1F300 && rune <= 0x1FAFF);
