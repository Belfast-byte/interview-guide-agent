package interview.guide.modules.interview.agent.adaptive.tool;

import java.util.List;

/**
 * 题库搜索源接口。
 */
public interface QuestionBankSearchSource {

  List<QuestionBankQuestion> search(String query, String difficulty);
}
