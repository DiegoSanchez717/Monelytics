package dev.monelytics;

import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class CategoryDefaults {
  private final CategoryRepository categories;

  CategoryDefaults(CategoryRepository categories) {
    this.categories = categories;
  }

  @Transactional
  void seed(AppUser user) {
    add(user, "Salary", CategoryType.INCOME, "#48D1CC");
    add(user, "Other income", CategoryType.INCOME, "#6AA9E9");
    add(user, "Housing", CategoryType.EXPENSE, "#EF7A6C");
    add(user, "Groceries", CategoryType.EXPENSE, "#48D1CC");
    add(user, "Dining", CategoryType.EXPENSE, "#E7B55F");
    add(user, "Transport", CategoryType.EXPENSE, "#6AA9E9");
    add(user, "Utilities", CategoryType.EXPENSE, "#A48ADE");
    add(user, "Shopping", CategoryType.EXPENSE, "#D580AD");
    add(user, "Subscriptions", CategoryType.EXPENSE, "#78B99A");
    add(user, "Health", CategoryType.EXPENSE, "#5F9FA9");
  }

  private void add(AppUser user, String name, CategoryType type, String color) {
    String key = name.toLowerCase(Locale.ROOT);
    if (categories.existsByUserIdAndNameKey(user.id, key)) return;
    FinanceCategory category = new FinanceCategory();
    category.user = user;
    category.name = name;
    category.nameKey = key;
    category.type = type;
    category.color = color;
    categories.save(category);
  }
}
