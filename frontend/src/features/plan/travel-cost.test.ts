import { describe, expect, it } from "vitest";
import { DEFAULT_TRAVEL_SETTINGS, ROAD_FACTOR, estimateTravel } from "./travel-cost";

const car = { ...DEFAULT_TRAVEL_SETTINGS, mode: "CAR" as const, kmPerLiter: 10, fuelPrice: 6 };

describe("estimateTravel", () => {
  it("charges the fuel of the extra street distance, round trip", () => {
    const estimate = estimateTravel(car, 12, 14, 4, 2);

    expect(estimate.extraKm).toBeCloseTo(10 * ROAD_FACTOR);
    // 13 km at 10 km/l and R$ 6 the litre.
    expect(estimate.extraCost).toBe(7.8);
    expect(estimate.netSavings).toBe(4.2);
  });

  it("charges one more bus ride per extra stop, whatever the distance", () => {
    const estimate = estimateTravel({ ...car, mode: "BUS", busFare: 5 }, 12, 30, 4, 3);

    expect(estimate.extraCost).toBe(10);
    expect(estimate.netSavings).toBe(2);
  });

  it("walking costs nothing and keeps the whole saving", () => {
    expect(estimateTravel({ ...car, mode: "WALK" }, 8.5, 6, 2, 2)).toMatchObject({ extraCost: 0, netSavings: 8.5 });
  });

  it("a split that is shorter than the single store adds no distance", () => {
    expect(estimateTravel(car, 3, 2, 5, 2)).toMatchObject({ extraKm: 0, extraCost: 0, netSavings: 3 });
  });

  it("says when the trip eats the saving", () => {
    expect(estimateTravel(car, 2, 20, 2, 2).netSavings).toBeLessThan(0);
  });
});
