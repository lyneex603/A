package ru.reportmenu;

import java.util.UUID;

public class Report {

    public enum Status { NEW, IN_PROGRESS, CLOSED, FALSE }

    public final int id;
    public UUID reporterId;
    public String reporterName;
    public UUID targetId;
    public String targetName;
    public String reasonKey = "";
    public String reason = "";
    public String comment = "";
    public long time;
    public Status status = Status.NEW;
    public String handler = "";
    public String closedBy = "";
    public long closedTime;
    /** Узнал ли автор о результате. */
    public boolean notified;

    public Report(int id) {
        this.id = id;
    }

    public boolean isOpen() {
        return status == Status.NEW || status == Status.IN_PROGRESS;
    }
}
