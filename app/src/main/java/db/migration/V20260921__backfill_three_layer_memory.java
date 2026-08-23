package db.migration;

import interview.guide.modules.interview.agent.adaptive.persistence.memory.migration.ThreeLayerMemoryHistoryBackfill;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * Flyway 入口：确定性回填历史三层记忆，不调用任何外部服务。
 */
public final class V20260921__backfill_three_layer_memory extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    new ThreeLayerMemoryHistoryBackfill().migrate(context.getConnection());
  }
}
