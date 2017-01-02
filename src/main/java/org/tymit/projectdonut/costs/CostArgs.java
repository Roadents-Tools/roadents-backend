package org.tymit.projectdonut.costs;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by ilan on 7/7/16.
 */
public class CostArgs {
    private final Map<String, Object> args;
    private String costTag;
    private Object subject;

    public CostArgs() {
        this.args = new HashMap<>();
    }

    public String getCostTag() {
        return costTag;
    }

    public void setCostTag(String costTag) {
        this.costTag = costTag;
    }

    public Object getSubject() {
        return subject;
    }

    public void setSubject(Object subject) {
        this.subject = subject;
    }

    public Map<String, Object> getArgs() {
        return args;
    }

}
