package db.migration;

import java.sql.*;
import java.util.*;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Preserves existing retirement portfolios while expanding the same exact-money ledger. */
public class V4__general_personal_finance extends BaseJavaMigration {
  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    // V1's inline CHECK names differ between PostgreSQL and H2. Discover only the three
    // legacy account checks and transaction type check, without changing old migration checksums.
    removeLegacyChecks(connection, "ira_accounts", List.of("ROTH_IRA", "BALANCE"));
    removeLegacyChecks(connection, "ledger_transactions", List.of("CONTRIBUTION"));
    String sql =
        """
        ALTER TABLE ira_accounts RENAME TO financial_accounts;
        ALTER TABLE retirement_goals RENAME TO savings_goals;
        ALTER TABLE financial_accounts ADD CONSTRAINT chk_financial_account_type CHECK(type IN ('CHECKING','SAVINGS','CREDIT_CARD','ROTH_IRA','TRADITIONAL_IRA'));
        ALTER TABLE financial_accounts ADD CONSTRAINT chk_financial_balance CHECK(type='CREDIT_CARD' OR balance>=0);
        ALTER TABLE financial_accounts ADD CONSTRAINT chk_financial_opening CHECK(type='CREDIT_CARD' OR opening_balance>=0);
        ALTER TABLE ledger_transactions ADD CONSTRAINT chk_ledger_type CHECK(type IN ('INCOME','EXPENSE','TRANSFER','CONTRIBUTION','WITHDRAWAL','ROLLOVER','RETURN'));
        ALTER TABLE savings_goals ADD COLUMN opening_amount NUMERIC(19,2) NOT NULL DEFAULT 0;
        UPDATE savings_goals SET opening_amount=current_amount;
        ALTER TABLE savings_goals ADD CONSTRAINT chk_goal_opening CHECK(opening_amount>=0);
        CREATE TABLE finance_categories (
          id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES app_users(id), name VARCHAR(80) NOT NULL,
          name_key VARCHAR(80) NOT NULL, type VARCHAR(12) NOT NULL CHECK(type IN ('INCOME','EXPENSE')),
          color VARCHAR(7) NOT NULL, CONSTRAINT uq_category_name UNIQUE(user_id,name_key)
        );
        CREATE TABLE monthly_budgets (
          id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES app_users(id), category_id UUID REFERENCES finance_categories(id),
          budget_month VARCHAR(7) NOT NULL, scope_key VARCHAR(36) NOT NULL, limit_amount NUMERIC(19,2) NOT NULL CHECK(limit_amount>0),
          version BIGINT NOT NULL DEFAULT 0, CONSTRAINT uq_budget_scope UNIQUE(user_id,budget_month,scope_key),
          CONSTRAINT chk_budget_scope CHECK((category_id IS NULL AND scope_key='ALL') OR (category_id IS NOT NULL AND scope_key<>'ALL'))
        );
        CREATE TABLE recurring_items (
          id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES app_users(id), account_id UUID NOT NULL REFERENCES financial_accounts(id),
          category_id UUID NOT NULL REFERENCES finance_categories(id), name VARCHAR(100) NOT NULL,
          kind VARCHAR(16) NOT NULL CHECK(kind IN ('BILL','SUBSCRIPTION')), frequency VARCHAR(16) NOT NULL CHECK(frequency IN ('MONTHLY','YEARLY')),
          amount NUMERIC(19,2) NOT NULL CHECK(amount>0), next_due_date DATE NOT NULL, anchor_day INTEGER NOT NULL CHECK(anchor_day BETWEEN 1 AND 31), active BOOLEAN NOT NULL, version BIGINT NOT NULL DEFAULT 0
        );
        ALTER TABLE ledger_transactions ADD COLUMN destination_account_id UUID REFERENCES financial_accounts(id);
        ALTER TABLE ledger_transactions ADD COLUMN category_id UUID REFERENCES finance_categories(id);
        ALTER TABLE ledger_transactions ADD COLUMN recurring_id UUID REFERENCES recurring_items(id) ON DELETE SET NULL;
        ALTER TABLE ledger_transactions ADD COLUMN recurring_due_date DATE;
        ALTER TABLE ledger_transactions ADD CONSTRAINT uq_recurring_payment UNIQUE(recurring_id,recurring_due_date);
        ALTER TABLE ledger_transactions ADD CONSTRAINT chk_transfer_pair CHECK((type='TRANSFER' AND destination_account_id IS NOT NULL AND destination_account_id<>account_id AND category_id IS NULL) OR (type<>'TRANSFER' AND destination_account_id IS NULL));
        CREATE TABLE goal_contributions (
          id UUID PRIMARY KEY, goal_id UUID NOT NULL REFERENCES savings_goals(id) ON DELETE CASCADE,
          amount NUMERIC(19,2) NOT NULL CHECK(amount>0), contribution_date DATE NOT NULL, note VARCHAR(240) NOT NULL,
          created_at TIMESTAMP WITH TIME ZONE NOT NULL
        );
        CREATE INDEX idx_category_user ON finance_categories(user_id);
        CREATE INDEX idx_budget_user_month ON monthly_budgets(user_id,budget_month);
        CREATE INDEX idx_recurring_user_due ON recurring_items(user_id,next_due_date);
        CREATE INDEX idx_transactions_category_date ON ledger_transactions(category_id,transaction_date);
        CREATE INDEX idx_transactions_destination ON ledger_transactions(destination_account_id);
        CREATE INDEX idx_goal_contributions_goal_date ON goal_contributions(goal_id,contribution_date);
        """;
    try (Statement statement = connection.createStatement()) {
      for (String command : sql.split(";")) if (!command.isBlank()) statement.execute(command);
    }
  }

  private void removeLegacyChecks(Connection connection, String table, List<String> clauses)
      throws SQLException {
    String query =
        """
        SELECT tc.constraint_name, cc.check_clause FROM information_schema.table_constraints tc
        JOIN information_schema.check_constraints cc ON tc.constraint_schema=cc.constraint_schema AND tc.constraint_name=cc.constraint_name
        WHERE lower(tc.table_name)=? AND tc.table_schema=? AND tc.constraint_type='CHECK'
        """;
    List<String> names = new ArrayList<>();
    try (PreparedStatement statement = connection.prepareStatement(query)) {
      statement.setString(1, table);
      statement.setString(2, connection.getSchema());
      try (ResultSet result = statement.executeQuery()) {
        while (result.next()) {
          String clause = result.getString(2).toUpperCase(Locale.ROOT);
          if (clauses.stream()
              .anyMatch(
                  token ->
                      clause.contains(token)
                          && (!token.equals("BALANCE") || clause.contains(">="))))
            names.add(result.getString(1));
        }
      }
    }
    try (Statement statement = connection.createStatement()) {
      for (String name : names)
        statement.execute(
            "ALTER TABLE " + table + " DROP CONSTRAINT \"" + name.replace("\"", "\"\"") + "\"");
    }
  }
}
