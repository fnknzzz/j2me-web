import javax.microedition.midlet.*;
import javax.microedition.lcdui.*;

public class FormChoiceGroupSuite extends MIDlet {
    private Display display;
    private Form form;

    public FormChoiceGroupSuite() {
        display = Display.getDisplay(this);
    }

    protected void startApp() {
        System.out.println("=== Form ChoiceGroup Test ===");
        
        form = new Form("ChoiceGroup Test");
        testChoiceGroup();
        
        System.out.println("Form ChoiceGroup test completed");
    }
    
    private void testChoiceGroup() {
        System.out.println("Test: ChoiceGroup");
        
        ChoiceGroup choiceGroup = new ChoiceGroup("Options:", Choice.EXCLUSIVE);
        choiceGroup.append("Option 1", null);
        choiceGroup.append("Option 2", null);
        choiceGroup.append("Option 3", null);
        
        form.append(choiceGroup);
        
        System.out.print("ChoiceGroup label: ");
        System.out.println(choiceGroup.getLabel());
        System.out.print("Size: ");
        System.out.println(choiceGroup.size());
        
        choiceGroup.setSelectedIndex(1, true);
        System.out.print("Selected index: ");
        System.out.println(choiceGroup.getSelectedIndex());
        System.out.print("Is selected (1): ");
        System.out.println(choiceGroup.isSelected(1));
        
        System.out.print("Option text (1): ");
        System.out.println(choiceGroup.getString(1));
    }

    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}
}
