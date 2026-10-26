import { describe, expect, it } from "vitest";
import { distanceKm, googleMapsDirectionsUrl, planRoute, routeLength } from "./route-planner";

const home = { latitude: -22.5, longitude: -44.1 };
// About 1.1 km per 0.01 degree of latitude.
const near = { latitude: -22.51, longitude: -44.1, name: "near" };
const far = { latitude: -22.53, longitude: -44.1, name: "far" };

describe("planRoute", () => {
  it("visits the nearer store first when that is the shorter way", () => {
    const route = planRoute(home, [far, near]);

    expect(route.map((leg) => leg.stop.name)).toEqual(["near", "far"]);
    expect(routeLength(route)).toBeCloseTo(distanceKm(home, far));
  });

  it("measures each leg from the previous stop", () => {
    const route = planRoute(home, [near, far]);

    expect(route[0].legKm).toBeCloseTo(distanceKm(home, near));
    expect(route[1].legKm).toBeCloseTo(distanceKm(near, far));
  });
});

describe("distanceKm", () => {
  it("is about 1.1 km for 0.01 degree of latitude", () => {
    expect(distanceKm(home, near)).toBeCloseTo(1.11, 1);
  });
});

describe("googleMapsDirectionsUrl", () => {
  it("ends at the last stop and passes the others as waypoints", () => {
    const url = new URL(googleMapsDirectionsUrl(home, [near, far]));

    expect(url.searchParams.get("destination")).toBe("-22.53,-44.1");
    expect(url.searchParams.get("waypoints")).toBe("-22.51,-44.1");
  });
});
