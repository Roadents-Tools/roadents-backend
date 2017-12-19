package com.reroute.backend.logic;

import com.reroute.backend.model.routing.TravelRoute;

import java.util.Collections;
import java.util.List;

public final class ApplicationResult {

    private final List<Exception> errors;
    private final List<TravelRoute> result;

    private ApplicationResult(List<TravelRoute> result, List<Exception> errors) {
        this.errors = errors != null ? Collections.unmodifiableList(errors) : Collections.emptyList();
        this.result = result != null ? Collections.unmodifiableList(result) : Collections.emptyList();
    }

    public static ApplicationResult ret(List<TravelRoute> routes) {
        return new ApplicationResult(routes, null);
    }

    public static ApplicationResult err(List<Exception> exceptions) {
        return new ApplicationResult(null, exceptions);
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public ApplicationResult withErrors(List<Exception> errors) {
        return new ApplicationResult(result, errors);
    }

    @Override
    public int hashCode() {
        int result1 = getErrors().hashCode();
        result1 = 31 * result1 + getResult().hashCode();
        return result1;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ApplicationResult that = (ApplicationResult) o;

        return getErrors().equals(that.getErrors()) && getResult().equals(that.getResult());
    }

    public List<Exception> getErrors() {
        return errors;
    }

    public List<TravelRoute> getResult() {
        return result;
    }
}
