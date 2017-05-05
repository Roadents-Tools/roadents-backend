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

    public CostArgs setCostTag(String costTag) {
        this.costTag = costTag;
        return this;
    }

    public Object getSubject() {
        return subject;
    }

    public CostArgs setSubject(Object subject) {
        this.subject = subject;
        return this;
    }

    public Map<String, Object> getArgs() {
        return args;
    }

    public CostArgs setArg(String key, Object value) {
        args.put(key, value);
        return this;
    }

}
