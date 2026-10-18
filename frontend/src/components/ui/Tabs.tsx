import React from 'react';

export interface TabItem {
  id: string;
  label: string;
  count?: number;
  icon?: string;
}

export interface TabsProps {
  tabs: TabItem[];
  activeTab: string;
  onChange: (tabId: string) => void;
  className?: string;
}

export const Tabs: React.FC<TabsProps> = ({ tabs, activeTab, onChange, className = '' }) => {
  return (
    <div className={`border-b border-outline-warm ${className}`}>
      <nav className="flex space-x-6 overflow-x-auto no-scrollbar" role="tablist">
        {tabs.map((tab) => {
          const isActive = tab.id === activeTab;
          return (
            <button
              key={tab.id}
              role="tab"
              aria-selected={isActive}
              onClick={() => onChange(tab.id)}
              className={`py-3 px-1 border-b-2 font-medium text-xs sm:text-sm whitespace-nowrap transition-colors flex items-center gap-2 ${
                isActive
                  ? 'border-primary text-primary font-semibold'
                  : 'border-transparent text-slate-muted hover:text-slate-text hover:border-outline'
              }`}
            >
              {tab.icon && (
                <span className="material-symbols-outlined !text-[17px]">{tab.icon}</span>
              )}
              <span>{tab.label}</span>
              {tab.count !== undefined && (
                <span
                  className={`text-[11px] px-1.5 py-0.5 rounded-full font-mono ${
                    isActive
                      ? 'bg-primary-tint text-primary'
                      : 'bg-[#F2F5F3] text-slate-muted'
                  }`}
                >
                  {tab.count}
                </span>
              )}
            </button>
          );
        })}
      </nav>
    </div>
  );
};
