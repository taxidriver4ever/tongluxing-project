import 'package:flutter_test/flutter_test.dart';
import 'package:tongluxing_frontend_flutter/common/utils/display_text.dart';

void main() {
  test('中文昵称超过六个汉字时省略', () {
    expect(compactDisplayName('一二三四五六七'), '一二三…六七');
  });

  test('英文昵称超过八个字母时省略', () {
    expect(compactDisplayName('abcdefghijk'), 'abcd…ijk');
  });

  test('短昵称保持原样，空昵称使用兜底', () {
    expect(compactDisplayName('同路行'), '同路行');
    expect(compactDisplayName('  '), '同路行用户');
  });
}
