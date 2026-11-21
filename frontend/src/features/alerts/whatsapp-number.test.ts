import { describe, expect, it } from "vitest";
import { formatWhatsAppNumber, isMobileWithDdd } from "./whatsapp-number";

describe("isMobileWithDdd", () => {
  it.each(["(24) 99999-8888", "24999998888", "+55 24 99999-8888"])("accepts %s", (typed) => {
    expect(isMobileWithDdd(typed)).toBe(true);
  });

  it.each(["(24) 3333-4444", "99999-8888", "(04) 99999-8888", ""])("refuses %s", (typed) => {
    expect(isMobileWithDdd(typed)).toBe(false);
  });
});

describe("formatWhatsAppNumber", () => {
  it("shows the saved number the way people write it", () => {
    expect(formatWhatsAppNumber("+5524999998888")).toBe("(24) 99999-8888");
  });
});
