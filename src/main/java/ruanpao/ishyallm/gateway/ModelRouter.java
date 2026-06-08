package ruanpao.ishyallm.gateway;

import org.springframework.stereotype.Service;
import java.util.List;

@org.springframework.stereotype.Service
public class ModelRouter {

    static final List<String> FACTUAL_KEYWORDS = List.of("定义", "症状", "正常值", "病因", "分类", "临床表现");
    static final List<String> REASONING_KEYWORDS = List.of("鉴别诊断", "治疗方案", "预后", "并发症", "手术", "药物");

    public String route(String query) {
        int len = query.length();

        if (len <= 10) return "flash";
        if (len > 20) return "pro";

        // 11~20 字
        for (String kw : REASONING_KEYWORDS) {
            if (query.contains(kw)) return "pro";
        }
        for (String kw : FACTUAL_KEYWORDS) {
            if (query.contains(kw)) return "flash";
        }
        return "pro"; // 无明确关键词默认 Pro
    }
}
