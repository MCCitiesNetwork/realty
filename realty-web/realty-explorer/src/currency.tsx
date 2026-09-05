import { createContext, useContext, type ReactNode } from "react";

const CurrencyContext = createContext<string>("");

/** What goes in front of every price on this deployment; "" where none is configured. */
export function CurrencyProvider({ value, children }: { value: string; children: ReactNode }) {
  return <CurrencyContext.Provider value={value}>{children}</CurrencyContext.Provider>;
}

export function useCurrency(): string {
  return useContext(CurrencyContext);
}
