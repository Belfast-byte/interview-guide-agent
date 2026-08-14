package interview.guide.modules.interview.agent.adaptive.tool;

import java.util.List;

public interface QuestionBankSearchSource {

  List<QuestionBankQuestion> search(String query, String difficulty);
}
