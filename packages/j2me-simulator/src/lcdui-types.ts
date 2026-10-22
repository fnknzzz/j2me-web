export const LcduiCommandType = {
  SCREEN: 1,
  BACK: 2,
  CANCEL: 3,
  OK: 4,
  HELP: 5,
  STOP: 6,
  EXIT: 7,
  ITEM: 8,
} as const;

export type LcduiCommandType =
  (typeof LcduiCommandType)[keyof typeof LcduiCommandType];

export const LcduiDateFieldMode = {
  DATE: 1,
  TIME: 2,
  DATE_TIME: 3,
} as const;

export type LcduiDateFieldMode =
  (typeof LcduiDateFieldMode)[keyof typeof LcduiDateFieldMode];

export const LcduiAlertType = {
  INFO: 1,
  WARNING: 2,
  ERROR: 3,
  ALARM: 4,
  CONFIRMATION: 5,
} as const;

export type LcduiAlertType =
  (typeof LcduiAlertType)[keyof typeof LcduiAlertType];

export const LcduiChoiceType = {
  EXCLUSIVE: 1,
  MULTIPLE: 2,
  IMPLICIT: 3,
  POPUP: 4,
} as const;

export type LcduiChoiceType =
  (typeof LcduiChoiceType)[keyof typeof LcduiChoiceType];

export const LcduiListType = {
  EXCLUSIVE: 1,
  MULTIPLE: 2,
  IMPLICIT: 3,
} as const;

export type LcduiListType = (typeof LcduiListType)[keyof typeof LcduiListType];

export type J2MEHandle = number;

export type LcduiDisplayableKind =
  | 'canvas'
  | 'form'
  | 'list'
  | 'textbox'
  | 'alert';

export interface LcduiCommandSnapshot {
  handle: J2MEHandle;
  label: string;
  longLabel?: string;
  type: LcduiCommandType;
  priority: number;
}

export interface LcduiSoftKeyBarSnapshot {
  left?: string;
  right?: string;
  leftCommandHandle?: number;
  rightCommandHandle?: number;
  leftKind?: 'command' | 'menu';
  rightKind?: 'command' | 'menu';
  menuCommands?: LcduiCommandSnapshot[];
}

export type LcduiItemKind =
  | 'stringItem'
  | 'imageItem'
  | 'spacer'
  | 'textField'
  | 'choiceGroup'
  | 'gauge'
  | 'dateField'
  | 'customItem';

export interface LcduiFontSnapshot {
  face: number;
  style: number;
  size: number;
}

export interface LcduiItemBaseSnapshot {
  kind: LcduiItemKind;
  handle: J2MEHandle;
  label?: string;
  focused?: boolean;
  layoutFlags?: number;
  font?: LcduiFontSnapshot;
}

export interface LcduiStringItemSnapshot extends LcduiItemBaseSnapshot {
  kind: 'stringItem';
  text: string;
  appearanceMode?: number;
}

export interface LcduiImageItemSnapshot extends LcduiItemBaseSnapshot {
  kind: 'imageItem';
  imageHandle?: J2MEHandle;
  altText?: string;
  appearanceMode?: number;
}

export interface LcduiSpacerSnapshot extends LcduiItemBaseSnapshot {
  kind: 'spacer';
  minWidth: number;
  minHeight: number;
}

export interface LcduiTextFieldSnapshot extends LcduiItemBaseSnapshot {
  kind: 'textField';
  text: string;
  maxSize: number;
  constraints: number;
  caretPosition?: number;
  initialInputMode?: string;
  inputType?: 'text' | 'password' | 'tel' | 'email' | 'url' | 'number';
}

export interface LcduiChoiceGroupOptionSnapshot {
  text: string;
  selected: boolean;
  disabled?: boolean;
  imageHandle?: J2MEHandle;
}

export interface LcduiChoiceGroupSnapshot extends LcduiItemBaseSnapshot {
  kind: 'choiceGroup';
  choiceType: LcduiChoiceType;
  options: LcduiChoiceGroupOptionSnapshot[];
  selectedIndex?: number;
}

export interface LcduiGaugeSnapshot extends LcduiItemBaseSnapshot {
  kind: 'gauge';
  interactive: boolean;
  maxValue: number;
  value: number;
}

export interface LcduiDateFieldSnapshot extends LcduiItemBaseSnapshot {
  kind: 'dateField';
  inputMode: LcduiDateFieldMode;
  date?: number;
}

export interface LcduiCustomItemSnapshot extends LcduiItemBaseSnapshot {
  kind: 'customItem';
  surfaceId: number;
  contentWidth: number;
  contentHeight: number;
}

export type LcduiItemSnapshot =
  | LcduiStringItemSnapshot
  | LcduiImageItemSnapshot
  | LcduiSpacerSnapshot
  | LcduiTextFieldSnapshot
  | LcduiChoiceGroupSnapshot
  | LcduiGaugeSnapshot
  | LcduiDateFieldSnapshot
  | LcduiCustomItemSnapshot;

export interface LcduiTickerSnapshot {
  text: string;
}

export interface LcduiFormSnapshot {
  kind: 'form';
  displayableHandle: J2MEHandle;
  title?: string;
  ticker?: LcduiTickerSnapshot;
  items: LcduiItemSnapshot[];
  commands: LcduiCommandSnapshot[];
  softKeys?: LcduiSoftKeyBarSnapshot;
  scroll?: { y: number; viewportHeight: number; contentHeight: number };
}

export interface LcduiListElementSnapshot {
  text: string;
  imageHandle?: J2MEHandle;
}

export interface LcduiListSnapshot {
  kind: 'list';
  displayableHandle: J2MEHandle;
  title?: string;
  ticker?: LcduiTickerSnapshot;
  listType: LcduiListType;
  elements: LcduiListElementSnapshot[];
  selectedIndex: number;
  selectedFlags?: boolean[];
  commands: LcduiCommandSnapshot[];
  softKeys?: LcduiSoftKeyBarSnapshot;
  scroll?: { firstVisibleIndex: number; visibleCount: number };
}

export interface LcduiTextBoxSnapshot {
  kind: 'textbox';
  displayableHandle: J2MEHandle;
  title?: string;
  ticker?: LcduiTickerSnapshot;
  text: string;
  maxSize: number;
  constraints: number;
  caretPosition?: number;
  commands: LcduiCommandSnapshot[];
  softKeys?: LcduiSoftKeyBarSnapshot;
}

export interface LcduiAlertSnapshot {
  kind: 'alert';
  displayableHandle: J2MEHandle;
  title?: string;
  ticker?: LcduiTickerSnapshot;
  text?: string;
  imageHandle?: J2MEHandle;
  alertType?: LcduiAlertType;
  timeout?: number;
  indicator?: LcduiGaugeSnapshot;
  modal?: boolean;
  commands: LcduiCommandSnapshot[];
  softKeys?: LcduiSoftKeyBarSnapshot;
}

export interface LcduiCanvasMarkerSnapshot {
  kind: 'canvas';
  displayableHandle: J2MEHandle;
  softKeys?: LcduiSoftKeyBarSnapshot;
}

export type LcduiViewSnapshot =
  | LcduiFormSnapshot
  | LcduiListSnapshot
  | LcduiTextBoxSnapshot
  | LcduiAlertSnapshot
  | LcduiCanvasMarkerSnapshot;

export type LcduiEvent =
  | {
      type: 'command';
      displayableHandle: J2MEHandle;
      commandHandle: J2MEHandle;
    }
  | {
      type: 'choiceGroupChanged';
      displayableHandle: J2MEHandle;
      itemHandle: J2MEHandle;
      selectedIndex?: number;
      selectedFlags?: boolean[];
    }
  | {
      type: 'gaugeChanged';
      displayableHandle: J2MEHandle;
      itemHandle: J2MEHandle;
      value: number;
    }
  | {
      type: 'dateFieldChanged';
      displayableHandle: J2MEHandle;
      itemHandle: J2MEHandle;
      date: number;
    }
  | {
      type: 'itemActivated';
      displayableHandle: J2MEHandle;
      itemHandle: J2MEHandle;
    }
  | {
      type: 'itemStateChanged';
      displayableHandle: J2MEHandle;
      itemHandle: J2MEHandle;
    }
  | {
      type: 'textFieldChanged';
      displayableHandle: J2MEHandle;
      itemHandle: J2MEHandle;
      text: string;
      caretPosition?: number;
    }
  | {
      type: 'textBoxChanged';
      displayableHandle: J2MEHandle;
      text: string;
      caretPosition?: number;
    }
  | {
      type: 'listSelectionChanged';
      displayableHandle: J2MEHandle;
      selectedIndex: number;
      selectedFlags?: boolean[];
    }
  | {
      type: 'listSelectionConfirmed';
      displayableHandle: J2MEHandle;
      selectedIndex: number;
    }
  | {
      type: 'scroll';
      displayableHandle: J2MEHandle;
      y: number;
    }
  | {
      type: 'softKey';
      displayableHandle: J2MEHandle;
      which: 'left' | 'right';
    }
  | {
      type: 'customItemPointer';
      displayableHandle: J2MEHandle;
      itemHandle: J2MEHandle;
      action: 'pressed' | 'released' | 'dragged';
      x: number;
      y: number;
    }
  | {
      type: 'customItemKey';
      displayableHandle: J2MEHandle;
      itemHandle: J2MEHandle;
      action: 'pressed' | 'released' | 'repeated';
      keyCode: number;
    };
