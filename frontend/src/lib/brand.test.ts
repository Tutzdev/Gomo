import { describe, expect, it } from "vitest";
import { formatUpdatedAt } from "./brand";

const now = new Date(2026, 10, 25, 14, 30);

describe("formatUpdatedAt", () => {
  it("says today with the time for a price seen today", () => {
    expect(formatUpdatedAt(new Date(2026, 10, 25, 11, 9), now)).toBe("hoje às 11:09");
  });

  it("says yesterday for a price seen the day before, even less than 24 hours ago", () => {
    expect(formatUpdatedAt(new Date(2026, 10, 24, 21, 35), now)).toBe("ontem às 21:35");
  });

  it("shows the full date from two days ago on", () => {
    expect(formatUpdatedAt(new Date(2026, 10, 23, 8, 0), now)).toBe("em 23/11/2026 às 08:00");
  });

  it("reads an ISO string from the API", () => {
    expect(formatUpdatedAt(new Date(2026, 10, 25, 9, 5).toISOString(), now)).toBe("hoje às 09:05");
  });

  it("says when there is no date", () => {
    expect(formatUpdatedAt(null, now)).toBe("Não informado");
  });
});
