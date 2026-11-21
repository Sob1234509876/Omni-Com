export type User = {
  id: number;
  authorities: string[];
  locked: boolean;
  enabled: boolean;
  expired: boolean;
  username: string;
  password: string;
};
