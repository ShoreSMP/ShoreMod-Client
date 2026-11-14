package net.shoresmp.core;

import java.math.*;
import java.util.*;

public final class Payments {
    public record Payment(Wire.User user, BigDecimal amount) {}
    public static BigDecimal amount(String input) {
        if (input==null || !input.matches("(?i)[0-9]{1,16}(\\.[0-9]{1,14})?[kmbt]?")) throw new IllegalArgumentException("Use an amount such as 500, 2.5m or 5b");
        char last=Character.toLowerCase(input.charAt(input.length()-1));
        int power=switch(last){case 'k'->3;case 'm'->6;case 'b'->9;case 't'->12;default->0;};
        BigDecimal value;
        try{value=new BigDecimal(power==0?input:input.substring(0,input.length()-1)).scaleByPowerOfTen(power).setScale(2,RoundingMode.UNNECESSARY);}
        catch(ArithmeticException e){throw new IllegalArgumentException("Amount must resolve to whole cents");}
        if (value.signum() <= 0 || value.compareTo(new BigDecimal("1000000000000000"))>0) throw new IllegalArgumentException("Amount must be positive and at most 1000t");
        return value;
    }
    /** Exact compact display: no rounding that could hide a recipient's actual payment. */
    public static String format(BigDecimal amount){
        String[] suffix={"t","b","m","k"};int[] power={12,9,6,3};
        for(int i=0;i<power.length;i++)if(amount.abs().compareTo(BigDecimal.TEN.pow(power[i]))>=0)return amount.scaleByPowerOfTen(-power[i]).stripTrailingZeros().toPlainString()+suffix[i];
        return amount.stripTrailingZeros().toPlainString();
    }
    public static List<Payment> each(List<Wire.User> users, BigDecimal amount) {
        requireUsers(users);
        return users.stream().map(u -> new Payment(u, amount)).toList();
    }
    public static List<Payment> split(List<Wire.User> users, BigDecimal total, Random random) {
        requireUsers(users);
        long cents = total.movePointRight(2).longValueExact();
        if (cents < users.size()) throw new IllegalArgumentException("Budget must cover at least $0.01 per recipient");
        long remaining = cents;
        var result = new ArrayList<Payment>();
        for (int i = 0; i < users.size(); i++) {
            int left = users.size() - i;
            long share;
            if (left == 1) share = remaining;
            else if (random == null) share = cents / users.size() + (i < cents % users.size() ? 1 : 0);
            else {
                long cap = Math.min(remaining - left + 1, Math.max(1, remaining / left * 2));
                share = random.nextLong(cap) + 1;
            }
            result.add(new Payment(users.get(i), BigDecimal.valueOf(share, 2)));
            remaining -= share;
        }
        return List.copyOf(result);
    }
    public static BigDecimal total(List<Payment> plan) {
        return plan.stream().map(Payment::amount).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2);
    }
    private static void requireUsers(List<Wire.User> users) {
        if (users.isEmpty()) throw new IllegalArgumentException("No eligible ShoreMod users in tab");
        if (users.stream().map(Wire.User::id).distinct().count() != users.size()) throw new IllegalArgumentException("Duplicate recipients");
    }
}
