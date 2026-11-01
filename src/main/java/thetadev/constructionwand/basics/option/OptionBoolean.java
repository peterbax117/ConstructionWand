package thetadev.constructionwand.basics.option;

import net.minecraft.nbt.CompoundTag;

public class OptionBoolean implements IOption<Boolean>
{
    private final CompoundTag tag;
    private final Runnable onChange;
    private final String key;
    private final boolean enabled;
    private boolean value;

    public OptionBoolean(CompoundTag tag, Runnable onChange, String key, boolean dval, boolean enabled) {
        this.tag = tag;
        this.onChange = onChange;
        this.key = key;
        this.enabled = enabled;

        if(tag.contains(key)) value = tag.getBoolean(key);
        else value = dval;
    }

    public OptionBoolean(CompoundTag tag, Runnable onChange, String key, boolean dval) {
        this(tag, onChange, key, dval, true);
    }

    @Override
    public String getKey() {
        return key;
    }

    @Override
    public String getValueString() {
        return value ? "yes" : "no";
    }

    @Override
    public void setValueString(String val) {
        set(val.equals("yes"));
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void set(Boolean val) {
        if(!enabled) return;
        value = val;
        tag.putBoolean(key, value);
        onChange.run();
    }

    @Override
    public Boolean get() {
        return value;
    }

    @Override
    public Boolean next(boolean dir) {
        set(!value);
        return value;
    }
}
