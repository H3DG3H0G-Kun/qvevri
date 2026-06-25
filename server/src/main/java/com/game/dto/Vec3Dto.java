package com.game.dto;

public class Vec3Dto {

    private double x;
    private double y;
    private double z;

    public Vec3Dto() {}

    public Vec3Dto(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static Vec3Dto zero() {
        return new Vec3Dto(0.0, 0.0, 0.0);
    }

    public double getX() { return x; }
    public void setX(double x) { this.x = x; }

    public double getY() { return y; }
    public void setY(double y) { this.y = y; }

    public double getZ() { return z; }
    public void setZ(double z) { this.z = z; }
}
