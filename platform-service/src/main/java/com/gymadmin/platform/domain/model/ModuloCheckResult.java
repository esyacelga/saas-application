package com.gymadmin.platform.domain.model;

import java.io.Serializable;

public class ModuloCheckResult implements Serializable {

    private Boolean permitido;
    private String plan;
    private String razon;

    public ModuloCheckResult() {}

    public ModuloCheckResult(Boolean permitido, String plan, String razon) {
        this.permitido = permitido;
        this.plan = plan;
        this.razon = razon;
    }

    public static ModuloCheckResult allowed(String plan) {
        return new ModuloCheckResult(true, plan, null);
    }

    public static ModuloCheckResult denied(String razon) {
        return new ModuloCheckResult(false, null, razon);
    }

    public Boolean getPermitido() { return permitido; }
    public void setPermitido(Boolean permitido) { this.permitido = permitido; }

    public String getPlan() { return plan; }
    public void setPlan(String plan) { this.plan = plan; }

    public String getRazon() { return razon; }
    public void setRazon(String razon) { this.razon = razon; }
}
