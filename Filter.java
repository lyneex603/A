package ru.reportmenu;

public record Filter(Type type, String value) {

    public enum Type { NONE, TARGET, AUTHOR, REASON }

    public static final Filter NONE = new Filter(Type.NONE, "");

    public boolean matches(Report r) {
        return switch (type) {
            case NONE -> true;
            case TARGET -> r.targetName.equalsIgnoreCase(value);
            case AUTHOR -> r.reporterName.equalsIgnoreCase(value);
            case REASON -> r.reasonKey.equalsIgnoreCase(value);
        };
    }
}
