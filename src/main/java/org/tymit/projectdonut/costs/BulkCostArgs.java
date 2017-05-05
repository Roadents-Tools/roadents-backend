package org.tymit.projectdonut.costs;

import com.google.common.collect.Sets;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by ilan on 5/5/17.
 */
public class BulkCostArgs {

    private final Map<String, Object> args;
    private String costTag;
    private Set<Object> subjects;

    public BulkCostArgs(CostArgs base) {
        this();
        setCostTag(base.getCostTag());
        addSubject(base.getSubject());
        base.getArgs().forEach((s, o) -> getArgs().put(s, o));
    }

    public BulkCostArgs() {
        this.args = new HashMap<>();
        subjects = Sets.newConcurrentHashSet();
    }

    public BulkCostArgs addSubject(Object subject) {
        this.subjects.add(subject);
        return this;
    }

    public Map<String, Object> getArgs() {
        return args;
    }

    public BulkCostArgs setArg(String key, Object value) {
        args.put(key, value);
        return this;
    }

    public Map<Object, CostArgs> splitSingular() {
        Map<Object, CostArgs> rval = new ConcurrentHashMap<>();
        for (Object subj : getSubjects()) {
            CostArgs newArgs = new CostArgs().setCostTag(getCostTag()).setSubject(subj);
            getArgs().forEach(newArgs::setArg);
            rval.put(subj, newArgs);
        }
        return rval;
    }

    public String getCostTag() {
        return costTag;
    }

    public BulkCostArgs setCostTag(String costTag) {
        this.costTag = costTag;
        return this;
    }

    public Set<Object> getSubjects() {
        return subjects;
    }
}
