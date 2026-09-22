package dev.joyswe.deepswe.source;

/**
 * Next.js RSC（React Server Components）flight payload 提取工具。
 *
 * <p>RSC 页面把数据以 {@code self.__next_f.push([1,"..."])} 片段写入 HTML：
 * 每个片段是 JSON 字符串字面量（内部经反斜杠转义），需逐个拼接并反转义后，
 * 才能得到可检索的 flight 文本。随后按字段名定位 {@code "xxx":{ 或 "xxx":[}
 * 并用括号配对提取完整 JSON 对象/数组。</p>
 *
 * <p>线性手工扫描（不用正则），避免 Java regex 对超长 flight 内容灾难性回溯。</p>
 */
public final class RscFlightParser {

    private RscFlightParser() {
    }

    /** 拼接所有 flight 片段并反转义，得到可检索的 RSC 文本。 */
    public static String concatFlightPayload(String html) {
        StringBuilder sb = new StringBuilder(html.length());
        String marker = "self.__next_f.push([1,\"";
        int from = 0;
        while (true) {
            int start = html.indexOf(marker, from);
            if (start < 0) {
                break;
            }
            int i = start + marker.length();
            StringBuilder raw = new StringBuilder();
            boolean escaped = false;
            while (i < html.length()) {
                char c = html.charAt(i);
                if (escaped) {
                    // 保留转义对（\" \\ 等），交给 unescapeJson 统一反转义
                    raw.append('\\').append(c);
                    escaped = false;
                    i++;
                } else if (c == '\\') {
                    escaped = true;
                    i++;
                } else if (c == '"') {
                    break; // flight 字符串字面量闭合引号
                } else {
                    raw.append(c);
                    i++;
                }
            }
            sb.append(unescapeJson(raw.toString()));
            from = i + 1;
        }
        if (sb.isEmpty()) {
            throw new IllegalStateException("flight payload not found");
        }
        return sb.toString();
    }

    /** 在 RSC 文本中定位 {@code "field":{ 并以括号配对提取完整 JSON 对象。 */
    public static String extractObject(String flight, String field) {
        int idx = flight.indexOf("\"" + field + "\":{");
        if (idx < 0) {
            throw new IllegalStateException("\"" + field + "\" object not found in flight payload");
        }
        int start = flight.indexOf('{', idx);
        return extractBalanced(flight, start);
    }

    /** 在 RSC 文本中定位 {@code "field":[ 并以括号配对提取完整 JSON 数组。 */
    public static String extractArray(String flight, String field) {
        int idx = flight.indexOf("\"" + field + "\":[");
        if (idx < 0) {
            throw new IllegalStateException("\"" + field + "\" array not found in flight payload");
        }
        int start = flight.indexOf('[', idx);
        return extractBalanced(flight, start);
    }

    /** 提取 {@code "field":[} 数组中「包含指定键名」的那一个（页面可能含精简版与完整版两个
     *  同名数组，如 Artificial Analysis 的 models：前者仅 slug/name，后者含全部指标）。
     *  逐个出现位置做括号配对提取，检查是否包含 {@code requiredKey}（如 {@code intelligenceIndex}）。 */
    public static String extractArrayContaining(String flight, String field, String requiredKey) {
        String needle = "\"" + field + "\":[";
        int from = 0;
        while (true) {
            int idx = flight.indexOf(needle, from);
            if (idx < 0) {
                throw new IllegalStateException(
                    "\"" + field + "\" array containing \"" + requiredKey + "\" not found in flight payload");
            }
            int start = flight.indexOf('[', idx);
            String json = extractBalanced(flight, start);
            if (json.contains(requiredKey)) {
                return json;
            }
            from = idx + needle.length();
        }
    }

    /** 从 start 处的 '{' 或 '[' 开始，返回配对的完整 JSON 对象/数组（跳过字符串与转义）。
     *  通过首个开括号字符决定结束符号（'{' → '}'，'[' → ']'）。 */
    private static String extractBalanced(String s, int start) {
        char open = s.charAt(start);
        char close = (open == '{') ? '}' : ']';
        int depth = 0;
        boolean inStr = false;
        boolean esc = false;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inStr) {
                if (esc) {
                    esc = false;
                } else if (c == '\\') {
                    esc = true;
                } else if (c == '"') {
                    inStr = false;
                }
            } else if (c == '"') {
                inStr = true;
            } else if (c == open) {
                depth++;
            } else if (c == close) {
                depth--;
                if (depth == 0) {
                    return s.substring(start, i + 1);
                }
            }
        }
        throw new IllegalStateException("unbalanced json in flight payload");
    }

    /** 反转义 JSON 字符串字面量（\" \\ \\/ \\n \\r \\t \\u005cXXXX 形式）。 */
    public static String unescapeJson(String s) {
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '\\' || i + 1 >= s.length()) {
                out.append(c);
                continue;
            }
            char n = s.charAt(i + 1);
            switch (n) {
                case '"' -> {
                    out.append('"');
                    i++;
                }
                case '\\' -> {
                    out.append('\\');
                    i++;
                }
                case '/' -> {
                    out.append('/');
                    i++;
                }
                case 'n' -> {
                    out.append('\n');
                    i++;
                }
                case 'r' -> {
                    out.append('\r');
                    i++;
                }
                case 't' -> {
                    out.append('\t');
                    i++;
                }
                case 'u' -> {
                    if (i + 5 < s.length()) {
                        try {
                            out.append((char) Integer.parseInt(s.substring(i + 2, i + 6), 16));
                        } catch (NumberFormatException e) {
                            out.append('\\');
                        }
                        i += 5;
                    } else {
                        out.append('\\');
                    }
                }
                default -> out.append('\\');
            }
        }
        return out.toString();
    }
}